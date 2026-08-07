# Luồng cấp thẻ thay thế khi người dùng làm mất thẻ tháng

## 1. Vấn đề hiện tại

Hệ thống đã có luồng báo mất thẻ:

1. Tạo incident loại `LOST_CARD`.
2. Đổi `parking_card.status` của thẻ cũ sang `LOST`.
3. Ngăn tạo nhiều báo cáo mất thẻ chưa xử lý cho cùng một thẻ.
4. Thu phí mất thẻ và đóng incident sau khi xe đã checkout.

Phần còn thiếu là cấp RFID mới cho vé tháng đang còn hiệu lực. Hiện tại `MonthlyTicket.parkingCard` vẫn trỏ đến thẻ cũ đã `LOST`, nên người dùng không có thẻ vật lý hợp lệ để tiếp tục sử dụng thời gian còn lại.

## 2. Nghiệp vụ đề xuất

Với người dùng có vé tháng/VIP/nhân viên còn hiệu lực:

1. Người dùng hoặc staff báo mất thẻ.
2. Backend khóa ngay thẻ cũ sang `LOST`.
3. Xe đang trong bãi phải checkout bằng biển số và hoàn tất phí mất thẻ.
4. Manager mở incident và chọn **Cấp thẻ thay thế**.
5. Manager chọn một RFID `AVAILABLE`, đúng chi nhánh và đúng loại thẻ.
6. Backend chuyển liên kết của vé tháng hiện tại từ thẻ cũ sang thẻ mới trong một transaction.
7. Thời gian `startDate/endDate`, gói giá, phương tiện và request phát hành giữ nguyên.
8. Thẻ cũ tiếp tục ở trạng thái `LOST`, không được chuyển về `AVAILABLE`.
9. Incident lưu cả thẻ cũ và thẻ thay thế để audit.
10. Incident chỉ được `RESOLVED` sau khi đã cấp thẻ mới hoặc manager chọn kết thúc thuê bao không cấp lại.

Không tạo `MonthlyTicket` mới và không cộng thêm thời gian khi thay thẻ. Đây là thay vật mang RFID, không phải mua mới hoặc gia hạn.

## 3. Quy tắc nghiệp vụ bắt buộc

- Chỉ thay thẻ cho incident `LOST_CARD` chưa `RESOLVED/CANCELLED`.
- Thẻ cũ phải có trạng thái `LOST`.
- Vé liên kết với thẻ cũ phải chưa bị soft-delete và còn hiệu lực.
- Không còn `ParkingSession.ACTIVE` sử dụng thẻ cũ.
- Thẻ mới phải `AVAILABLE`.
- Thẻ mới phải cùng chi nhánh với thẻ cũ/vé tháng.
- Thẻ mới phải cùng nhóm loại: `MONTHLY -> MONTHLY`, `VIP -> VIP`, `EMPLOYEE -> EMPLOYEE`.
- Thẻ mới không được có vé tháng active khác.
- Khóa pessimistic cả thẻ cũ và thẻ mới để tránh hai manager cấp cùng một RFID.
- Một incident chỉ được cấp thẻ thay thế một lần.
- Chỉ `MANAGER` hoặc `ADMIN` được cấp thẻ thay thế.

## 4. Thay đổi database

### Phương án đơn giản: lưu trên incident_report

```sql
ALTER TABLE incident_report
ADD replacement_card_id BIGINT NULL,
    replacement_ticket_id BIGINT NULL,
    replacement_at DATETIME2 NULL,
    replacement_by_user_id BIGINT NULL;
```

Tên bảng thực tế cần đối chiếu với annotation `@Table` của `IncidentReport` trước khi chạy migration.

Thêm khóa ngoại:

```sql
ALTER TABLE incident_report
ADD CONSTRAINT FK_incident_replacement_card
    FOREIGN KEY (replacement_card_id)
    REFERENCES parking_card(parking_card_id);

ALTER TABLE incident_report
ADD CONSTRAINT FK_incident_replacement_ticket
    FOREIGN KEY (replacement_ticket_id)
    REFERENCES monthly_ticket(ticket_id);

ALTER TABLE incident_report
ADD CONSTRAINT FK_incident_replacement_by
    FOREIGN KEY (replacement_by_user_id)
    REFERENCES users(user_id);
```

`parking_card_id` hiện có trong incident tiếp tục đại diện cho thẻ bị mất. Không ghi đè nó bằng thẻ mới.

Nếu dự kiến một vé có thể mất thẻ nhiều lần, mỗi lần tạo một incident riêng; lịch sử thay thế được giữ theo từng incident.

## 5. Sửa model backend

File: `src/main/java/Parking/Model/IncidentReport.java`

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "replacement_card_id")
private ParkingCard replacementCard;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "replacement_ticket_id")
private MonthlyTicket replacementTicket;

@Column(name = "replacement_at")
private LocalDateTime replacementAt;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "replacement_by_user_id")
private User replacementBy;
```

Không thay đổi trường `parkingCard`; trường đó phải giữ thẻ cũ bị mất.

## 6. DTO request và response

### Request mới

File mới: `dto/request/ReplaceLostMonthlyCardRequest.java`

```java
@Data
public class ReplaceLostMonthlyCardRequest {
    @NotNull(message = "Phải chọn thẻ RFID thay thế")
    private Long replacementCardId;
}
```

### Bổ sung IncidentReportResponse

```java
private Long replacementCardId;
private String replacementCardCode;
private Long replacementTicketId;
private LocalDateTime replacementAt;
private Long replacementByUserId;
private String replacementByName;
private Boolean monthlyCardReplacementRequired;
```

`monthlyCardReplacementRequired=true` khi incident là mất thẻ, có vé tháng còn hiệu lực trên thẻ cũ nhưng chưa có `replacementCard`.

## 7. Repository backend

### MonthlyTicketRepository

Thêm truy vấn lấy đúng vé active đang gắn với thẻ bị mất. Truy vấn vẫn phải cho phép `parking_card.status=LOST`; chỉ trạng thái vé và thời gian quyết định thuê bao còn hiệu lực.

```java
@Query("""
    SELECT mt FROM MonthlyTicket mt
    JOIN FETCH mt.vehicle vehicle
    WHERE mt.deleted = false
      AND mt.parkingCard.parkingCardId = :cardId
      AND mt.status = Parking.enums.MonthlyTicketStatus.ACTIVE
      AND mt.startDate <= :time
      AND mt.endDate >= :time
    ORDER BY mt.createdAt DESC, mt.ticketId DESC
""")
List<MonthlyTicket> findActiveTicketsForLostCard(
        @Param("cardId") Long cardId,
        @Param("time") LocalDateTime time);
```

Nếu chưa triển khai soft delete thì tạm bỏ điều kiện `mt.deleted=false`, nhưng nên bổ sung theo tài liệu xóa vé tháng.

### ParkingCardRepository

Tái sử dụng `findByIdForUpdate()` đã có:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT card FROM ParkingCard card WHERE card.parkingCardId = :id")
Optional<ParkingCard> findByIdForUpdate(@Param("id") Long id);
```

### IncidentReportRepository

Nên thêm truy vấn khóa incident:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT report FROM IncidentReport report WHERE report.incidentId = :id")
Optional<IncidentReport> findByIdForUpdate(@Param("id") Long id);
```

## 8. Service cấp thẻ thay thế

Nên tạo service riêng `LostMonthlyCardReplacementService` để không làm `IncidentReportService` quá lớn.

```java
@Service
@RequiredArgsConstructor
public class LostMonthlyCardReplacementService {
    private final IncidentReportRepository incidentRepository;
    private final ParkingCardRepository cardRepository;
    private final MonthlyTicketRepository ticketRepository;
    private final ParkingSessionRepository sessionRepository;
    private final BranchScopeService branchScopeService;
    private final CurrentUserService currentUserService;

    @Transactional
    public IncidentReportResponse replaceCard(
            Long incidentId,
            Long replacementCardId) {

        IncidentReport incident = incidentRepository
                .findByIdForUpdate(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy báo cáo mất thẻ"));

        if (incident.getIncidentType() != IncidentType.LOST_CARD) {
            throw new InvalidTicketStateException(
                    "Sự cố này không phải báo mất thẻ");
        }

        if (incident.getStatus() == IncidentStatus.RESOLVED
                || incident.getStatus() == IncidentStatus.CANCELLED) {
            throw new InvalidTicketStateException(
                    "Sự cố đã kết thúc, không thể cấp thẻ thay thế");
        }

        if (incident.getReplacementCard() != null) {
            throw new InvalidTicketStateException(
                    "Sự cố này đã được cấp thẻ thay thế");
        }

        ParkingCard oldCard = cardRepository
                .findByIdForUpdate(incident.getParkingCard().getParkingCardId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thẻ bị mất"));

        if (oldCard.getStatus() != ParkingCardStatus.LOST) {
            throw new InvalidTicketStateException(
                    "Thẻ cũ chưa được khóa ở trạng thái LOST");
        }

        branchScopeService.assertSameBranch(
                oldCard.getParkingBranch().getParkingBranchId());

        if (sessionRepository
                .existsByParkingCardParkingCardIdAndStatus(
                        oldCard.getParkingCardId(),
                        ParkingSessionStatus.ACTIVE)) {
            throw new InvalidTicketStateException(
                    "Phải checkout xe và hoàn tất xử lý mất thẻ trước khi cấp thẻ mới");
        }

        List<MonthlyTicket> activeTickets = ticketRepository
                .findActiveTicketsForLostCard(
                        oldCard.getParkingCardId(), LocalDateTime.now());

        if (activeTickets.isEmpty()) {
            throw new InvalidTicketStateException(
                    "Thẻ bị mất không có vé tháng còn hiệu lực");
        }
        if (activeTickets.size() > 1) {
            throw new InvalidTicketStateException(
                    "Dữ liệu không hợp lệ: thẻ cũ có nhiều vé đang hoạt động");
        }

        MonthlyTicket ticket = activeTickets.get(0);

        ParkingCard newCard = cardRepository
                .findByIdForUpdate(replacementCardId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thẻ thay thế"));

        if (newCard.getStatus() != ParkingCardStatus.AVAILABLE) {
            throw new InvalidTicketStateException(
                    "Thẻ thay thế không khả dụng");
        }

        if (!oldCard.getParkingBranch().getParkingBranchId().equals(
                newCard.getParkingBranch().getParkingBranchId())) {
            throw new InvalidTicketStateException(
                    "Thẻ thay thế phải cùng chi nhánh với thẻ bị mất");
        }

        if (oldCard.getType() != newCard.getType()) {
            throw new InvalidTicketStateException(
                    "Loại thẻ thay thế không khớp với thẻ bị mất");
        }

        if (ticketRepository.existsActiveTicketByCard(
                newCard.getParkingCardId(), LocalDateTime.now())) {
            throw new InvalidTicketStateException(
                    "Thẻ thay thế đang được liên kết với vé tháng khác");
        }

        ticket.setParkingCard(newCard);
        ticketRepository.save(ticket);

        // AVAILABLE nghĩa là thẻ đã được cấp cho thuê bao nhưng xe chưa ở trong bãi.
        newCard.setStatus(ParkingCardStatus.AVAILABLE);
        cardRepository.save(newCard);

        // Tuyệt đối không mở lại thẻ cũ.
        oldCard.setStatus(ParkingCardStatus.LOST);
        cardRepository.save(oldCard);

        User manager = currentUserService.getCurrentUser();
        incident.setReplacementCard(newCard);
        incident.setReplacementTicket(ticket);
        incident.setReplacementAt(LocalDateTime.now());
        incident.setReplacementBy(manager);

        IncidentLog log = new IncidentLog();
        log.setChangedBy(manager);
        log.setChangedAt(LocalDateTime.now());
        log.setOldStatus(incident.getStatus());
        log.setNewStatus(incident.getStatus());
        log.setActionType(IncidentLogAction.UPDATE);
        log.setDescription(
                "Đã thay thẻ " + oldCard.getCardCode()
                + " bằng " + newCard.getCardCode()
                + " cho vé tháng #" + ticket.getTicketId());
        incident.addLog(log);

        return toResponse(incidentRepository.save(incident));
    }
}
```

`IncidentLogAction.UPDATE` cần khớp CHECK constraint hiện có. Nếu enum/database chưa có `UPDATE`, dùng action type hợp lệ hiện tại và phân biệt bằng `description`.

## 9. Controller backend

File: `IncidentReportController.java`

```java
@PutMapping("/{id}/replace-monthly-card")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public ResponseEntity<IncidentReportResponse> replaceLostMonthlyCard(
        @PathVariable Long id,
        @Valid @RequestBody ReplaceLostMonthlyCardRequest request) {
    return ResponseEntity.ok(
            replacementService.replaceCard(
                    id, request.getReplacementCardId()));
}
```

API:

```http
PUT /api/incidents/{incidentId}/replace-monthly-card
Content-Type: application/json

{
  "replacementCardId": 42
}
```

## 10. Điều chỉnh resolve incident

File: `IncidentReportService.resolveIncident()`.

Với incident mất thẻ có vé tháng còn hiệu lực, không cho resolve nếu chưa cấp thẻ thay thế:

```java
if (report.getIncidentType() == IncidentType.LOST_CARD) {
    boolean hasActiveMonthlyTicket = monthlyTicketRepository
            .findActiveTicketsForLostCard(
                    report.getParkingCard().getParkingCardId(),
                    LocalDateTime.now())
            .size() == 1;

    if (hasActiveMonthlyTicket && report.getReplacementCard() == null) {
        throw new InvalidTicketStateException(
                "Phải cấp thẻ thay thế trước khi hoàn tất sự cố mất thẻ");
    }
}
```

Nếu nghiệp vụ cho phép khách không nhận thẻ mới và muốn kết thúc thuê bao, cần một lựa chọn riêng `terminateMonthlyTicket=true`. Khi đó backend dừng vé (`INACTIVE`) trước khi resolve; không âm thầm bỏ qua bước cấp thẻ.

## 11. Điều chỉnh cancel incident

Chỉ cho hủy báo mất khi chưa cấp thẻ thay thế.

```java
if (report.getReplacementCard() != null) {
    throw new InvalidTicketStateException(
            "Không thể hủy báo mất sau khi đã cấp thẻ thay thế");
}
```

Khi hủy báo mất trước lúc thay thẻ, chỉ mở lại thẻ cũ nếu xác nhận tìm thấy thẻ. Trạng thái trả về:

- Có session active: `IN_USE`.
- Không có session active: `AVAILABLE`.

Không tự mở lại thẻ cũ sau khi đã cấp thẻ mới.

## 12. Frontend manager

File chính: `D:/Ki7/SWP391_G5_FE/src/modules/manager/components/IncidentPanel.jsx`.

### 12.1. Thêm API

File `src/modules/manager/api/manager.js`:

```javascript
replaceLostMonthlyCard: async (incidentId, replacementCardId) =>
  (await API.put(
    `/api/incidents/${incidentId}/replace-monthly-card`,
    { replacementCardId: Number(replacementCardId) }
  )).data,
```

### 12.2. Hiển thị nút cấp thẻ thay thế

Chỉ hiển thị khi:

```javascript
incident.incidentType === 'LOST_CARD' &&
incident.monthlyCardReplacementRequired === true &&
!incident.replacementCardId &&
!['RESOLVED', 'CANCELLED'].includes(incident.status)
```

```jsx
<Button
  variant="primary"
  onClick={() => openReplacementModal(incident)}
>
  Cấp thẻ thay thế
</Button>
```

### 12.3. Modal chọn RFID mới

Khi mở modal:

1. Gọi `managerApi.getParkingCards()`.
2. Lọc `status === 'AVAILABLE'`.
3. Lọc cùng `parkingBranchId` với incident.
4. Lọc cùng loại thẻ cũ.
5. Hiển thị mã thẻ và chi nhánh.

```javascript
const replacementCards = cards.filter(card =>
  card.status === 'AVAILABLE' &&
  String(card.parkingBranchId) === String(selectedIncident.parkingBranchId) &&
  card.type === selectedIncident.parkingCardType
);
```

Submit:

```javascript
const handleReplaceCard = async () => {
  if (!replacementCardId) {
    return toast.warn('Vui lòng chọn thẻ RFID thay thế');
  }

  try {
    await managerApi.replaceLostMonthlyCard(
      selectedIncident.incidentId,
      replacementCardId
    );
    toast.success('Đã cấp thẻ thay thế thành công');
    closeReplacementModal();
    await fetchIncidents();
  } catch (error) {
    const message =
      error?.response?.data?.message ||
      error?.response?.data ||
      'Không thể cấp thẻ thay thế';
    toast.error(String(message));
  }
};
```

Sau khi thành công, incident hiển thị:

```text
Thẻ bị mất: MONTH-CARDM018
Thẻ thay thế: MONTH-CARDM042
Vé tháng: #56
Xe: 59V254411
Thời hạn: giữ nguyên
```

## 13. Frontend người dùng

Trang hồ sơ/thẻ tháng cần hiển thị trạng thái trong thời gian chờ xử lý:

- RFID cũ: `Đã báo mất – đang chờ cấp lại`.
- Không hiển thị nút gia hạn hoặc sử dụng mã thẻ cũ trong lúc incident đang mở.
- Sau khi manager cấp lại, refresh dữ liệu vé để hiển thị `replacementCardCode` mới.

Nếu có trang chi tiết incident, hiển thị tiến trình:

```text
Đã báo mất → Thẻ cũ đã khóa → Đã checkout/đóng phí → Đã cấp thẻ mới → Hoàn tất
```

Không cho user tự chọn RFID mới; thẻ vật lý phải do manager tại đúng chi nhánh cấp.

## 14. Gate In/Gate Out

### Thẻ cũ

`ParkingSessionService` đã kiểm tra `parkingCard.status != AVAILABLE`, nên thẻ `LOST` phải bị từ chối với thông báo rõ:

```java
if (parkingCard.getStatus() == ParkingCardStatus.LOST) {
    throw new ParkingSessionException(
            "Thẻ đã được báo mất và không còn hiệu lực");
}
```

### Thẻ mới

Sau khi `MonthlyTicket.parkingCard` chuyển sang thẻ mới:

- Lookup RFID mới trả đúng xe của vé hiện tại.
- Lookup RFID cũ không được trả xe active.
- Check-in bằng thẻ mới giữ nguyên ngày hết hạn cũ.

## 15. Phí mất thẻ

Phí mất thẻ và cấp thẻ thay thế là hai phần nghiệp vụ khác nhau:

- `lostCardFee`: khoản bồi thường thẻ bị mất.
- Thay RFID: không mua lại gói tháng, không cộng thời gian.

Nếu chính sách yêu cầu thanh toán phí trước khi cấp thẻ mới, thêm điều kiện:

```java
if (incident.getStatus() == IncidentStatus.WAITING_PAYMENT) {
    throw new InvalidTicketStateException(
            "Phải hoàn tất phí mất thẻ trước khi cấp thẻ thay thế");
}
```

Nên lưu `lost_card_fee_paid_at` hoặc liên kết payment riêng thay vì suy luận chỉ từ số tiền.

## 16. Kịch bản kiểm thử bắt buộc

1. Báo mất thẻ tháng: thẻ cũ chuyển `LOST` ngay lập tức.
2. Quét thẻ cũ sau báo mất: backend từ chối.
3. Cấp thẻ mới khi xe còn session active: backend từ chối.
4. Cấp thẻ mới khác chi nhánh: backend từ chối.
5. Cấp thẻ mới sai loại: backend từ chối.
6. Cấp thẻ mới đang gắn vé active khác: backend từ chối.
7. Hai manager cùng chọn một thẻ mới: chỉ một transaction thành công.
8. Cấp thẻ thành công: `MonthlyTicket.parkingCard` trỏ thẻ mới.
9. Vé sau thay thẻ giữ nguyên xe, gói, `startDate` và `endDate`.
10. Incident giữ thẻ cũ và ghi nhận thẻ mới/người/thời gian thay.
11. Cấp lại lần hai trên cùng incident: backend từ chối.
12. Resolve trước khi cấp lại: backend từ chối nếu thuê bao còn hiệu lực.
13. Hủy incident sau khi đã cấp lại: backend từ chối.
14. Quét thẻ mới: tự điền đúng xe và check-in thành công.
15. Thẻ cũ vẫn `LOST` sau khi incident hoàn tất.
16. Vé đã hết hạn hoặc đã bị xóa: không cho cấp thẻ thay thế; hướng dẫn đăng ký mới.

## 17. File cần sửa khi triển khai

Backend:

- Migration cho `incident_report`.
- `Model/IncidentReport.java`
- `dto/request/ReplaceLostMonthlyCardRequest.java`
- `dto/response/IncidentReportResponse.java`
- `Repository/IncidentReportRepository.java`
- `Repository/MonthlyTicketRepository.java`
- `Service/LostMonthlyCardReplacementService.java`
- `Service/IncidentReportService.java`
- `Controller/IncidentReportController.java`
- `ParkingSessionService.java` để trả thông báo riêng cho thẻ `LOST`.

Frontend manager:

- `src/modules/manager/api/manager.js`
- `src/modules/manager/components/IncidentPanel.jsx`

Frontend user:

- Component hiển thị thẻ tháng trong profile/dashboard.
- Component hiển thị trạng thái incident nếu có.

## 18. Tiêu chí hoàn thành

- Báo mất khóa thẻ cũ ngay lập tức.
- Manager có luồng chọn và cấp RFID thay thế.
- Thẻ mới kế thừa đúng vé tháng hiện tại, không tạo hoặc gia hạn gói.
- Thời hạn vé không thay đổi.
- Thẻ cũ không thể sử dụng lại.
- Incident lưu đầy đủ thẻ cũ, thẻ mới, vé, manager và thời điểm thay.
- Không thể cấp trùng thẻ mới khi xử lý đồng thời.
- Frontend user và manager đều nhìn thấy trạng thái thay thẻ rõ ràng.
