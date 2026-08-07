# Xử lý quét thẻ tháng đã dừng hoặc hết hạn tại cổng vào

## 1. Mục tiêu

Khi staff nhập hoặc quét RFID của thẻ tháng:

- Vé đang hoạt động và còn hạn: tự điền đúng xe hiện tại và cho phép check-in.
- Vé đã bị manager dừng: hiển thị `Thẻ tháng đã bị dừng` và không cho check-in.
- Vé đã hết ngày hiệu lực: hiển thị `Thẻ tháng đã hết hạn` và không cho check-in.
- Vé chưa đến ngày hiệu lực: hiển thị `Thẻ tháng chưa đến ngày hiệu lực`.
- Thẻ chưa từng được cấp vé: hiển thị `Thẻ tháng chưa được liên kết với vé nào`.

Backend phải là nơi quyết định trạng thái cuối cùng. Frontend chỉ dùng kết quả backend để hiển thị; không tự tìm trong toàn bộ lịch sử rồi chọn bằng `find()`.

## 2. Lỗi trong luồng hiện tại

### Frontend

`GateInPanel.jsx` đang gọi `GET /api/monthly-tickets`, nhận toàn bộ lịch sử rồi tự tìm theo `cardCode`. Trước đây nó không lọc trạng thái nên có thể chọn xe cũ. Sau khi đã lọc vé active, nếu không tìm thấy vé active, code vẫn tiếp tục fallback sang `vehiclesList`. Fallback này có thể tìm lại liên kết lịch sử của thẻ và tự điền xe cũ.

Với mã bắt đầu bằng `MONTH-`, `VIP-` hoặc `EMP-`, tuyệt đối không fallback sang danh sách xe khi backend xác định vé đã dừng/hết hạn.

### Backend

`ParkingSessionService` hiện chỉ truy vấn vé đang active:

```java
findActiveTicketsByCard(parkingCardId, currentTime)
```

Nếu không có kết quả, backend trả thông báo chung:

```text
Thẻ tháng không có vé tháng hoạt động hoặc đã hết hạn
```

Thông báo này chưa phân biệt được manager đã dừng hay thời hạn đã hết. Ngoài ra frontend không có endpoint tra cứu trạng thái thẻ trước khi bấm xác nhận.

## 3. Thiết kế API đề xuất

Thêm endpoint dành cho thao tác quét thẻ:

```http
GET /api/monthly-tickets/card-lookup?cardCode=MONTH-CARDM018&time=2026-08-07T22:30:00
```

Không dùng `GET /api/monthly-tickets` để tra cứu tại cổng.

Response khi vé hợp lệ:

```json
{
  "lookupStatus": "ACTIVE",
  "message": "Thẻ tháng hợp lệ",
  "ticketId": 56,
  "parkingCardId": 26,
  "cardCode": "MONTH-CARDM018",
  "vehicleId": 97,
  "licensePlate": "59V254411",
  "vehicleColor": "Đen",
  "vehicleBrand": "Mec",
  "vehicleTypeId": 1,
  "startDate": "2026-08-07T22:12:00",
  "endDate": "2026-09-07T22:12:00"
}
```

Response cho vé bị dừng hoặc hết hạn vẫn nên dùng HTTP `200` vì đây là kết quả tra cứu nghiệp vụ, không phải lỗi hệ thống:

```json
{
  "lookupStatus": "STOPPED",
  "message": "Thẻ tháng đã bị manager dừng",
  "cardCode": "MONTH-CARDM018",
  "ticketId": 56
}
```

```json
{
  "lookupStatus": "EXPIRED",
  "message": "Thẻ tháng đã hết hạn",
  "cardCode": "MONTH-CARDM018",
  "ticketId": 56,
  "endDate": "2026-09-07T22:12:00"
}
```

Các giá trị `lookupStatus`:

```java
public enum MonthlyCardLookupStatus {
    ACTIVE,
    STOPPED,
    EXPIRED,
    NOT_STARTED,
    NOT_ASSIGNED
}
```

## 4. Sửa backend

### 4.1. Thêm repository query lấy lịch sử mới nhất theo thẻ

File: `src/main/java/Parking/Repository/MonthlyTicketRepository.java`

```java
@Query("""
    SELECT mt FROM MonthlyTicket mt
    JOIN FETCH mt.vehicle vehicle
    JOIN FETCH vehicle.vehicleType
    WHERE mt.parkingCard.parkingCardId = :parkingCardId
    ORDER BY mt.createdAt DESC, mt.ticketId DESC
""")
List<MonthlyTicket> findAllByCardOrderByNewest(
        @Param("parkingCardId") Long parkingCardId);
```

Không dùng `findFirst()` không có `ORDER BY`.

### 4.2. Tạo response DTO

File mới: `src/main/java/Parking/dto/response/MonthlyCardLookupResponse.java`

```java
@Data
@Builder
public class MonthlyCardLookupResponse {
    private String lookupStatus;
    private String message;
    private Long ticketId;
    private Long parkingCardId;
    private String cardCode;
    private Long vehicleId;
    private String licensePlate;
    private String vehicleColor;
    private String vehicleBrand;
    private Long vehicleTypeId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
```

### 4.3. Thêm hàm phân loại trạng thái trong service

File: `src/main/java/Parking/Service/MonthlyTicketService.java`

```java
@Transactional(readOnly = true)
public MonthlyCardLookupResponse lookupByCardCode(
        String rawCardCode, LocalDateTime requestedTime) {

    String cardCode = rawCardCode == null
            ? ""
            : rawCardCode.trim().toUpperCase();
    LocalDateTime time = requestedTime != null
            ? requestedTime
            : LocalDateTime.now();

    ParkingCard card = parkingCardRepository
            .findByCardCodeIgnoreCase(cardCode)
            .orElseThrow(() -> new ParkingSessionException(
                    "Không tìm thấy thẻ giữ xe"));

    branchScopeService.assertSameBranch(
            card.getParkingBranch().getParkingBranchId());

    List<MonthlyTicket> tickets = monthlyTicketRepository
            .findAllByCardOrderByNewest(card.getParkingCardId());

    if (tickets.isEmpty()) {
        return baseLookup(card, "NOT_ASSIGNED",
                "Thẻ tháng chưa được liên kết với vé nào", null);
    }

    List<MonthlyTicket> activeTickets = tickets.stream()
            .filter(ticket -> ticket.getStatus() == MonthlyTicketStatus.ACTIVE)
            .filter(ticket -> !ticket.getStartDate().isAfter(time))
            .filter(ticket -> !ticket.getEndDate().isBefore(time))
            .toList();

    if (activeTickets.size() > 1) {
        throw new InvalidTicketStateException(
                "Dữ liệu không hợp lệ: thẻ có nhiều vé tháng đang hoạt động");
    }

    if (activeTickets.size() == 1) {
        return activeLookup(card, activeTickets.get(0));
    }

    MonthlyTicket latest = tickets.get(0);

    if (latest.getStatus() == MonthlyTicketStatus.INACTIVE) {
        return baseLookup(card, "STOPPED",
                "Thẻ tháng đã bị manager dừng", latest);
    }

    if (latest.getEndDate() != null && latest.getEndDate().isBefore(time)) {
        return baseLookup(card, "EXPIRED",
                "Thẻ tháng đã hết hạn", latest);
    }

    if (latest.getStartDate() != null && latest.getStartDate().isAfter(time)) {
        return baseLookup(card, "NOT_STARTED",
                "Thẻ tháng chưa đến ngày hiệu lực", latest);
    }

    return baseLookup(card, "NOT_ASSIGNED",
            "Thẻ tháng không có vé hợp lệ", latest);
}
```

Thứ tự kiểm tra quan trọng:

1. Tìm vé active đúng thời điểm trước.
2. Nếu không có, lấy vé mới nhất để xác định `STOPPED`, `EXPIRED` hoặc `NOT_STARTED`.
3. Không lấy phương tiện của vé cũ làm phương tiện được phép check-in.

### 4.4. Thêm controller endpoint

File: `src/main/java/Parking/Controller/MonthlyTicketController.java`

```java
@GetMapping("/card-lookup")
@PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
public ResponseEntity<MonthlyCardLookupResponse> lookupByCardCode(
        @RequestParam String cardCode,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime time) {
    return ResponseEntity.ok(
            monthlyTicketService.lookupByCardCode(cardCode, time));
}
```

### 4.5. Backend check-in vẫn phải kiểm tra lại

File: `src/main/java/Parking/Service/ParkingSessionService.java`

Không được chỉ dựa vào frontend lookup. Ngay trong `guestCheckIn()`, khi thẻ là `MONTHLY`, phải phân loại lại:

```java
MonthlyCardLookupResponse lookup = monthlyTicketService
        .lookupByCardCode(parkingCard.getCardCode(), currentTime);

switch (lookup.getLookupStatus()) {
    case "STOPPED" -> throw new InvalidTicketStateException(
            "Thẻ tháng đã bị manager dừng");
    case "EXPIRED" -> throw new InvalidTicketStateException(
            "Thẻ tháng đã hết hạn");
    case "NOT_STARTED" -> throw new InvalidTicketStateException(
            "Thẻ tháng chưa đến ngày hiệu lực");
    case "NOT_ASSIGNED" -> throw new InvalidTicketStateException(
            "Thẻ tháng chưa được liên kết với vé nào");
    case "ACTIVE" -> {
        if (!lookup.getLicensePlate().equalsIgnoreCase(licensePlate)) {
            throw new InvalidTicketStateException(
                    "Biển số xe không khớp với thông tin đăng ký trên vé tháng");
        }
    }
    default -> throw new InvalidTicketStateException(
            "Trạng thái thẻ tháng không hợp lệ");
}
```

Nên tách phần phân loại thành method/domain object dùng chung thay vì để service gọi qua response DTO. Ví dụ tốt hơn:

```java
MonthlyCardValidationResult validateMonthlyCard(card, time)
```

Cả endpoint lookup và check-in cùng gọi method này để không lệch logic.

## 5. Sửa frontend

### 5.1. Thêm API tra cứu RFID

File: `D:/Ki7/SWP391_G5_FE/src/modules/staff/api/staffApi.js`

```javascript
lookupMonthlyCard: async (cardCode, time) => {
  return (await API.get('/api/monthly-tickets/card-lookup', {
    params: { cardCode, time }
  })).data;
},
```

### 5.2. Thay logic lookup trong GateInPanel

File: `D:/Ki7/SWP391_G5_FE/src/modules/staff/components/GateInPanel.jsx`

Thêm state:

```javascript
const [monthlyCardLookupStatus, setMonthlyCardLookupStatus] = useState(null);
const [monthlyCardError, setMonthlyCardError] = useState('');
```

Tạo hàm xóa dữ liệu xe tự điền:

```javascript
const clearRegisteredVehicle = () => {
  setLicensePlate('');
  setVehicleColor('');
  setVehicleBrand('');
  setIsAutoPopulated(false);
  setVipVehicles([]);
  setVipOwnerName('');
  setSelectedVipVehicleId('');
};
```

Với thẻ có prefix đăng ký, gọi endpoint mới trước:

```javascript
const lookupCardCode = async (cleanCode) => {
  const isRegisteredCard =
    cleanCode.startsWith('MONTH-') ||
    cleanCode.startsWith('VIP-') ||
    cleanCode.startsWith('EMP-');

  if (isRegisteredCard) {
    const result = await staffApi.lookupMonthlyCard(
      cleanCode,
      simulatedTime || undefined
    );

    setMonthlyCardLookupStatus(result.lookupStatus);

    if (result.lookupStatus !== 'ACTIVE') {
      clearRegisteredVehicle();
      setIsMonthlyOrVipCard(true);
      setMonthlyCardError(result.message);
      toast.error(result.message);
      return false;
    }

    setMonthlyCardError('');
    setLicensePlate(result.licensePlate || '');
    setVehicleColor(result.vehicleColor || '');
    setVehicleBrand(result.vehicleBrand || '');
    setVehicleTypeId(result.vehicleTypeId || '');
    setIsAutoPopulated(true);
    setIsMonthlyOrVipCard(true);
    toast.success(`Thẻ hợp lệ cho xe ${result.licensePlate}`);
    return true;
  }

  // Chỉ thẻ thường mới được đi theo luồng xe vãng lai.
  return false;
};
```

Xóa fallback hiện tại từ thẻ tháng sang `vehiclesList`. Đoạn dạng sau chỉ được chạy cho thẻ thường:

```javascript
const matchVehicle = vehiclesList.find(...);
```

### 5.3. Hiển thị cảnh báo trên form

```jsx
{monthlyCardError && (
  <Alert variant="danger" className="fw-bold">
    {monthlyCardError}
  </Alert>
)}
```

Khóa nút xác nhận nếu thẻ đăng ký không active:

```jsx
const registeredCardBlocked =
  checkIsMonthlyOrVip(cardCode) &&
  monthlyCardLookupStatus !== 'ACTIVE';

<Button
  disabled={submitting || registeredCardBlocked}
  onClick={handleConfirm}
>
  PHÁT THẺ & MỞ BARIE
</Button>
```

Khi thay đổi/xóa `cardCode`, phải reset `monthlyCardLookupStatus` và `monthlyCardError` để không giữ thông báo của lần quét trước.

### 5.4. Hiển thị lỗi backend khi xác nhận

Trong `catch` của `handleConfirm()`:

```javascript
const message =
  error?.response?.data?.message ||
  (typeof error?.response?.data === 'string'
    ? error.response.data
    : null) ||
  error?.message ||
  'Không thể check-in';

toast.error(message);
```

Điều này tương thích với cả `ErrorResponse` dạng JSON và các handler cũ đang trả plain text.

## 6. Không tự động đổi vé hết hạn thành INACTIVE khi chỉ tra cứu

Không nên cập nhật database trong API lookup. Một vé có thể có:

- `status = ACTIVE` nhưng `endDate < now`: trạng thái nghiệp vụ là `EXPIRED`.
- `status = INACTIVE` do manager bấm dừng: trạng thái nghiệp vụ là `STOPPED`.

Nếu muốn đồng bộ `status` của vé hết hạn về `INACTIVE`, dùng scheduled job riêng. Không gộp việc ghi dữ liệu vào thao tác quét thẻ.

## 7. Kịch bản kiểm thử

1. Vé `ACTIVE`, đang trong hạn: tự điền đúng biển số và cho check-in.
2. Manager dừng vé: quét lại phải báo `Thẻ tháng đã bị manager dừng`, các ô xe phải trống và nút xác nhận bị khóa.
3. Vé vẫn `ACTIVE` nhưng `endDate` đã qua: báo `Thẻ tháng đã hết hạn`.
4. Vé có `startDate` trong tương lai: báo chưa đến ngày hiệu lực.
5. Thẻ tháng chưa từng cấp vé: báo chưa liên kết.
6. Thẻ đã cấp cho xe A, dừng và cấp lại xe B: chỉ tự điền xe B.
7. Frontend bị sửa/bỏ qua kiểm tra và gọi check-in trực tiếp: backend vẫn từ chối thẻ dừng/hết hạn.
8. Thẻ có hai vé active cùng thời điểm: backend báo dữ liệu không hợp lệ, không tự chọn một xe.
9. Thẻ thường không có prefix đăng ký: vẫn đi theo luồng xe vãng lai.

## 8. File cần thay đổi khi triển khai

Backend:

- `MonthlyTicketRepository.java`
- `MonthlyTicketService.java`
- `MonthlyTicketController.java`
- `ParkingSessionService.java`
- `MonthlyCardLookupResponse.java` (file mới)
- `MonthlyCardLookupStatus.java` (file mới, nếu dùng enum)

Frontend:

- `src/modules/staff/api/staffApi.js`
- `src/modules/staff/components/GateInPanel.jsx`

## 9. Tiêu chí hoàn thành

- Frontend không còn tải toàn bộ lịch sử vé để xác định xe khi quét RFID.
- Thẻ bị dừng/hết hạn không tự điền thông tin xe cũ.
- Staff nhận được thông báo riêng cho `STOPPED`, `EXPIRED`, `NOT_STARTED` và `NOT_ASSIGNED`.
- Nút check-in bị khóa nếu thẻ đăng ký không `ACTIVE`.
- Backend luôn kiểm tra lại trạng thái trong transaction check-in.
- Không thể bypass bằng cách gọi API check-in trực tiếp.
