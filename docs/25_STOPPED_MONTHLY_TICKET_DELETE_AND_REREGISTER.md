# Làm lại tính năng xóa vé tháng đã tạm dừng và đăng ký lại từ đầu

## 1. Yêu cầu nghiệp vụ

Luồng mong muốn:

1. Manager bấm **Dừng** một vé tháng đang hoạt động.
2. Vé chuyển từ `ACTIVE` sang `INACTIVE`, thẻ RFID vật lý được giải phóng về `AVAILABLE`.
3. Chỉ khi vé đã `INACTIVE`, manager mới được bấm **Xóa**.
4. Sau khi xóa, vé cũ không còn xuất hiện trong danh sách và không còn được dùng để check-in, kích hoạt hoặc gia hạn.
5. Thẻ RFID vật lý không bị xóa; nó có thể được cấp cho một đăng ký mới.
6. Nếu người dùng đăng ký lại, hệ thống tạo `MonthlyTicketRequest` mới và sau khi duyệt tạo `MonthlyTicket` mới với thời gian mới tính từ thời điểm cấp.
7. Không cộng thời gian vào `endDate` của vé cũ.

## 2. Không hard-delete bản ghi vé khỏi database

Không nên gọi trực tiếp:

```java
monthlyTicketRepository.delete(ticket);
```

Vì `monthly_ticket_request.renewal_of_ticket_id` đang là khóa ngoại trỏ đến `monthly_ticket.ticket_id`. Vé đã từng được gia hạn sẽ gây lỗi constraint khi xóa. Nếu xóa cascade các request/payment thì sẽ mất lịch sử thanh toán.

Giải pháp đề xuất là **soft delete**:

- Bản ghi vẫn tồn tại để giữ lịch sử và khóa ngoại.
- Vé bị đánh dấu `deleted = true`.
- Mọi truy vấn phục vụ nghiệp vụ phải bỏ qua vé đã xóa.
- Frontend không còn hiển thị vé đã xóa.
- Đăng ký lại luôn tạo vé mới.

Trong tài liệu này, “Xóa vé” nghĩa là xóa logic `MonthlyTicket`, không xóa `ParkingCard` vật lý.

## 3. Database migration

Thêm các cột:

```sql
ALTER TABLE monthly_ticket
ADD deleted BIT NOT NULL
        CONSTRAINT DF_monthly_ticket_deleted DEFAULT 0,
    deleted_at DATETIME2 NULL,
    deleted_by_user_id BIGINT NULL;
```

Nếu muốn lưu người thực hiện và bảng `users` sử dụng `user_id`:

```sql
ALTER TABLE monthly_ticket
ADD CONSTRAINT FK_monthly_ticket_deleted_by_user
FOREIGN KEY (deleted_by_user_id) REFERENCES users(user_id);
```

Đồng bộ dữ liệu cũ:

```sql
UPDATE monthly_ticket
SET deleted = 0
WHERE deleted IS NULL;
```

Không cập nhật `monthly_ticket_request.renewal_of_ticket_id` và không xóa payment/request cũ.

## 4. Sửa entity backend

File: `src/main/java/Parking/Model/MonthlyTicket.java`

```java
@Column(name = "deleted", nullable = false)
private boolean deleted = false;

@Column(name = "deleted_at")
private LocalDateTime deletedAt;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "deleted_by_user_id")
private User deletedBy;
```

Không dùng `@SQLDelete` trong trường hợp này vì nghiệp vụ cần kiểm tra trạng thái, session và quyền trước khi xóa. Thực hiện soft delete rõ ràng trong service dễ kiểm soát hơn.

## 5. Sửa repository backend

File: `src/main/java/Parking/Repository/MonthlyTicketRepository.java`

### 5.1. Chỉ tìm vé chưa bị xóa

Các truy vấn sau phải thêm:

```sql
mt.deleted = false
```

Danh sách cần sửa:

- `findAllByBranchId`
- `existsActiveOverlapByVehicle`
- `existsActiveOverlapByCard`
- `existsActiveTicketByCard`
- `findActiveTicketsByCard`
- `findAllByUserId`
- `existsActiveEmployeeTicketByUserId`
- `existsActiveTicketByVehicle`
- Truy vấn lookup thẻ RFID.

Ví dụ:

```java
@Query("""
    SELECT mt FROM MonthlyTicket mt
    WHERE mt.deleted = false
      AND mt.parkingCard.parkingCardId = :parkingCardId
      AND mt.status = 1
      AND mt.startDate <= :time
      AND mt.endDate >= :time
    ORDER BY mt.createdAt DESC, mt.ticketId DESC
""")
List<MonthlyTicket> findActiveTicketsByCard(
        @Param("parkingCardId") Long parkingCardId,
        @Param("time") LocalDateTime time);
```

### 5.2. Tách truy vấn quản trị và truy vấn nghiệp vụ

Service xóa cần tìm được cả vé chưa xóa nhưng `INACTIVE`:

```java
Optional<MonthlyTicket> findByTicketIdAndDeletedFalse(Long ticketId);
```

Nếu cần màn hình lịch sử/audit:

```java
List<MonthlyTicket> findByDeletedTrueOrderByDeletedAtDesc();
```

Không dùng `findById()` tùy tiện trong nghiệp vụ check-in/gia hạn vì nó vẫn trả vé đã soft-delete.

## 6. Làm lại hàm xóa backend

File: `src/main/java/Parking/Service/MonthlyTicketService.java`

Inject thêm nếu chưa có:

```java
private final CurrentUserService currentUserService;
private final MonthlyTicketRequestRepository monthlyTicketRequestRepository;
```

Hàm xóa:

```java
@Transactional
public void deleteMonthlyTicket(Long ticketId) {
    MonthlyTicket ticket = monthlyTicketRepository
            .findByTicketIdAndDeletedFalse(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Không tìm thấy vé tháng"));

    ParkingCard card = ticket.getParkingCard();

    branchScopeService.assertSameBranch(
            card.getParkingBranch().getParkingBranchId());

    if (ticket.getStatus() != MonthlyTicketStatus.INACTIVE) {
        throw new InvalidTicketStateException(
                "Phải dừng vé tháng trước khi xóa");
    }

    if (parkingSessionRepository
            .existsByParkingCardParkingCardIdAndStatus(
                    card.getParkingCardId(), ParkingSessionStatus.ACTIVE)) {
        throw new InvalidTicketStateException(
                "Không thể xóa vé khi thẻ đang có phiên gửi xe hoạt động");
    }

    if (monthlyTicketRequestRepository
            .existsByRenewalOfTicketTicketIdAndStatusIn(
                    ticketId,
                    List.of(
                        MonthlyTicketRequestStatus.PENDING_PAYMENT,
                        MonthlyTicketRequestStatus.PENDING_APPROVAL))) {
        throw new InvalidTicketStateException(
                "Không thể xóa vé khi còn yêu cầu gia hạn đang xử lý");
    }

    ticket.setDeleted(true);
    ticket.setDeletedAt(LocalDateTime.now());
    ticket.setDeletedBy(currentUserService.getCurrentUser());
    ticket.setStatus(MonthlyTicketStatus.INACTIVE);
    monthlyTicketRepository.save(ticket);

    if (card.getStatus() != ParkingCardStatus.LOST
            && card.getStatus() != ParkingCardStatus.DISABLED) {
        card.setStatus(ParkingCardStatus.AVAILABLE);
        parkingCardRepository.save(card);
    }
}
```

Các điều kiện bắt buộc:

- Chỉ xóa vé `INACTIVE`.
- Không có session đang hoạt động.
- Không có request gia hạn `PENDING_PAYMENT` hoặc `PENDING_APPROVAL`.
- Thao tác chạy trong một transaction.
- Không xóa `ParkingCard`.

## 7. Quyền endpoint xóa

File: `src/main/java/Parking/Controller/MonthlyTicketController.java`

Nếu yêu cầu chỉ manager được xóa:

```java
@DeleteMapping("/{id}")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public ResponseEntity<Void> deleteMonthlyTicket(@PathVariable Long id) {
    monthlyTicketService.deleteMonthlyTicket(id);
    return ResponseEntity.noContent().build();
}
```

Không để `STAFF` xóa nếu nghiệp vụ yêu cầu manager chịu trách nhiệm giải phóng vé.

## 8. Ngăn gia hạn hoặc kích hoạt vé đã xóa

### 8.1. Renewal service

File: `src/main/java/Parking/Service/MonthlyTicketRenewalService.java`

Sau khi tìm vé:

```java
if (ticket.isDeleted()) {
    throw new InvalidTicketStateException(
            "Vé tháng đã bị xóa. Vui lòng đăng ký gói mới");
}
```

Không cho phép tạo `renewal_of_ticket_id` đến vé đã soft-delete.

### 8.2. Duyệt request

File: `src/main/java/Parking/Service/MonthlyTicketRequestService.java`

Trong nhánh:

```java
MonthlyTicket oldTicket = req.getRenewalOfTicket();
```

thêm:

```java
if (oldTicket != null && oldTicket.isDeleted()) {
    throw new InvalidTicketStateException(
            "Vé cũ đã bị xóa; yêu cầu này không thể xử lý như gia hạn");
}
```

Không tự đổi request gia hạn cũ thành đăng ký mới vì request có thể đã thanh toán với ngữ cảnh khác. Người dùng phải tạo yêu cầu đăng ký mới rõ ràng.

### 8.3. Update service

`updateMonthlyTicket()` cũng phải dùng:

```java
findByTicketIdAndDeletedFalse(id)
```

để không thể gọi API PUT và kích hoạt lại vé đã xóa.

## 9. Sửa frontend manager

File: `D:/Ki7/SWP391_G5_FE/src/modules/manager/components/MemberPanel.jsx`

### 9.1. Bỏ hành vi kích hoạt lại vé cũ

Hiện tại `handleToggleTicket()` làm hai việc:

- Vé active: gọi `/stop`.
- Vé inactive: gọi `PUT` với `status: 1` và ngày cũ.

Phải bỏ hoàn toàn nhánh kích hoạt lại:

```javascript
const handleStopTicket = async (ticket) => {
  const ticketId = getTicketId(ticket);
  if (!ticketId) return toast.error('Không tìm thấy ID vé tháng');

  try {
    await managerApi.stopMonthlyTicket(ticketId);
    toast.success('Đã tạm dừng vé tháng');
    await fetchAll();
  } catch (error) {
    toast.error(getApiMessage(error, 'Không thể dừng vé tháng'));
  }
};
```

Không còn đoạn:

```javascript
managerApi.updateMonthlyTicket(id, { status: 1, ... })
```

### 9.2. Thêm nút xóa cho vé đã dừng

```javascript
const handleDeleteStoppedTicket = async (ticket) => {
  const ticketId = getTicketId(ticket);
  if (!ticketId) return toast.error('Không tìm thấy ID vé tháng');

  const confirmed = window.confirm(
    `Xóa vé tháng của xe ${ticket.licensePlate || ''}? ` +
    'Thẻ RFID sẽ được giải phóng để cấp cho đăng ký mới.'
  );
  if (!confirmed) return;

  try {
    await managerApi.deleteMonthlyTicket(ticketId);
    toast.success('Đã xóa vé tháng và giải phóng thẻ RFID');
    await fetchAll();
  } catch (error) {
    toast.error(getApiMessage(error, 'Không thể xóa vé tháng'));
  }
};
```

Render nút:

```jsx
{isActive ? (
  <Button
    variant="link"
    className="text-warning fw-bold"
    onClick={() => handleStopTicket(t)}
  >
    Dừng
  </Button>
) : (
  <Button
    variant="link"
    className="text-danger fw-bold"
    onClick={() => handleDeleteStoppedTicket(t)}
  >
    Xóa
  </Button>
)}
```

Không hiển thị nút `Kích hoạt` cho vé đã dừng.

### 9.3. API frontend

File `src/modules/manager/api/manager.js` đã có:

```javascript
deleteMonthlyTicket: async (id) =>
  (await API.delete(`/api/monthly-tickets/${id}`)).data,
```

Giữ API này, chỉ thay handler gọi nó.

## 10. Làm lại nút đăng ký lại

“Đăng ký lại” phải là đăng ký mới, không phải gia hạn và không phải kích hoạt bản ghi cũ.

Frontend user cần gọi endpoint đăng ký mới hiện có:

```http
POST /api/monthly-ticket-requests
```

Payload phải chỉ chứa xe, gói và chi nhánh mới:

```json
{
  "vehicleId": 97,
  "policyId": 3,
  "branchId": 1
}
```

Không gửi:

- `ticketId` cũ.
- `renewalOfTicketId`.
- `startDate/endDate` cũ.
- Thao tác `PUT /api/monthly-tickets/{oldId}`.
- Endpoint `/renewal-requests`.

Khi manager duyệt request mới, backend phải đi vào nhánh:

```java
oldTicket == null
```

và tạo:

```java
MonthlyTicket newTicket = new MonthlyTicket();
newTicket.setStartDate(now);
newTicket.setEndDate(now.plusMonths(1));
```

Không chạy nhánh gia hạn:

```java
oldTicket.setEndDate(baseDate.plusMonths(1));
```

## 11. Thẻ RFID được cấp lại như thế nào

Sau khi soft-delete vé cũ:

- `parking_card.status = AVAILABLE`.
- Các repository kiểm tra vé active bỏ qua `deleted = true`.
- Manager có thể chọn lại cùng RFID khi duyệt request mới.
- Vé mới có `ticket_id` mới, `monthly_ticket_request_id` mới và thời gian mới.
- Vé cũ vẫn giữ lịch sử nhưng không thể check-in/gia hạn/kích hoạt.

Không cần đặt `monthly_ticket.parking_card_id = NULL`. Giữ liên kết cũ giúp truy vết thẻ nào từng được cấp cho vé nào.

## 12. Xử lý dữ liệu đã lỗi trước đây

Tìm vé inactive vẫn đang hiển thị để kích hoạt:

```sql
SELECT ticket_id, parking_card_id, vehicle_id,
       status, start_date, end_date
FROM monthly_ticket
WHERE status = 0
  AND deleted = 0;
```

Chỉ sau khi xác nhận manager muốn xóa logic các vé này:

```sql
UPDATE monthly_ticket
SET deleted = 1,
    deleted_at = GETDATE()
WHERE ticket_id IN (:confirmedTicketIds)
  AND status = 0;
```

Không cập nhật hàng loạt nếu chưa đối chiếu request gia hạn đang chờ xử lý.

## 13. Kịch bản kiểm thử bắt buộc

1. Xóa vé `ACTIVE`: backend từ chối với thông báo phải dừng trước.
2. Dừng vé có session active: backend từ chối.
3. Dừng vé không có session: vé thành `INACTIVE`, RFID thành `AVAILABLE`.
4. Xóa vé `INACTIVE`: `deleted=true`, vé biến mất khỏi danh sách.
5. Xóa vé có renewal request đang chờ: backend từ chối.
6. Vé đã xóa từng có payment/request: lịch sử vẫn xem được, không lỗi khóa ngoại.
7. Quét RFID sau khi xóa nhưng chưa cấp mới: báo chưa có vé hoạt động.
8. Đăng ký lại: tạo request mới, không có `renewalOfTicket`.
9. Duyệt đăng ký lại: tạo `ticket_id` mới với `startDate=now`.
10. Cùng RFID được cấp lại cho xe mới: check-in chỉ nhận xe mới.
11. Gọi PUT trực tiếp vào vé đã xóa: backend trả `404` hoặc lỗi vé đã xóa.
12. Gọi renewal endpoint với vé đã xóa: backend yêu cầu đăng ký gói mới.

## 14. File cần sửa khi triển khai

Backend:

- Migration SQL cho `monthly_ticket.deleted`, `deleted_at`, `deleted_by_user_id`.
- `Model/MonthlyTicket.java`
- `Repository/MonthlyTicketRepository.java`
- `Service/MonthlyTicketService.java`
- `Service/MonthlyTicketRenewalService.java`
- `Service/MonthlyTicketRequestService.java`
- `Controller/MonthlyTicketController.java`

Frontend manager:

- `src/modules/manager/components/MemberPanel.jsx`
- `src/modules/manager/api/manager.js` (API đã có, chỉ cần giữ/chuẩn hóa lỗi)

Frontend user đăng ký lại:

- Component đang render nút `Đăng ký lại`.
- Bảo đảm component gọi API đăng ký mới, không gọi renewal/update vé cũ.

## 15. Tiêu chí hoàn thành

- Chỉ vé đã dừng mới xóa được.
- Xóa vé không làm mất request/payment lịch sử.
- RFID vật lý được giải phóng và có thể cấp lại.
- Vé đã xóa không xuất hiện trong danh sách nghiệp vụ.
- Vé đã xóa không thể kích hoạt, gia hạn hoặc check-in.
- Frontend không còn nút `Kích hoạt` trên vé inactive; thay bằng `Xóa`.
- Đăng ký lại tạo request và ticket mới.
- Thời hạn mới không cộng dồn từ `endDate` của vé cũ.
