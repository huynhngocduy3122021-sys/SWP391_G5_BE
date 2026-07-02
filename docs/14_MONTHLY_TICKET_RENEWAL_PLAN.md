# Kế hoạch phát triển chức năng gia hạn vé tháng

Ngày lập: 2026-07-02

## 1. Mục tiêu

Phát triển chức năng để khi khách hàng đã có vé tháng và muốn đăng ký tiếp, hệ thống có thể **gia hạn thời gian sử dụng** thay vì luôn tạo một vé tháng mới hoặc báo lỗi trùng thời gian.

Ví dụ:

* Vé hiện tại: `2026-07-01T00:00:00` đến `2026-07-31T23:59:59`
* Khách gia hạn thêm 1 tháng vào ngày `2026-07-20`
* Vé sau gia hạn nên thành: `2026-07-01T00:00:00` đến khoảng cuối tháng 8, tùy rule tính tháng được chọn

## 2. Hiện trạng code

File chính:

* `src/main/java/Parking/Controller/MonthlyTicketController.java`
* `src/main/java/Parking/Service/MonthlyTicketService.java`
* `src/main/java/Parking/Repository/MonthlyTicketRepository.java`
* `src/main/java/Parking/dto/request/CreateMonthlyTicketRequest.java`
* `src/main/java/Parking/dto/request/UpdateMonthlyTicketRequest.java`

Hiện tại API tạo vé tháng:

```http
POST /api/monthly-tickets
```

Request hiện tại:

```json
{
  "vehicleId": 12,
  "parkingCardId": 5,
  "guestName": "Nguyễn Văn A",
  "guestPhone": "0987654321",
  "startDate": "2026-07-01T00:00:00",
  "endDate": "2026-07-31T23:59:59",
  "status": 1
}
```

Logic hiện tại:

* Nếu `startDate >= endDate` thì báo lỗi.
* Nếu thẻ không phải type `MONTHLY` thì báo lỗi.
* Nếu thẻ bị `LOST` hoặc `DISABLED` thì báo lỗi.
* Nếu branch của thẻ không hợp lệ hoặc user không có quyền branch đó thì báo lỗi.
* Nếu xe hoặc thẻ đã có vé tháng active bị giao thời gian với request mới thì báo lỗi.
* Nếu không bị lỗi thì tạo một bản ghi `MonthlyTicket` mới.

Kết luận: hiện tại hệ thống **chưa có logic tự cộng thêm thời gian/gia hạn**.

## 3. Vấn đề nghiệp vụ cần giải quyết

Khi khách đã có vé tháng active, nhân viên thường không muốn tạo một vé mới bị trùng thời gian. Nghiệp vụ mong muốn là:

* Nếu khách còn hạn và mua tiếp, hệ thống cộng thêm thời gian vào `endDate` hiện tại.
* Nếu khách đã hết hạn nhưng vẫn dùng cùng xe/thẻ, hệ thống có thể gia hạn từ thời điểm hiện tại hoặc tạo chu kỳ mới.
* Lịch sử vé tháng phải rõ ràng để biết khách đã mua/gia hạn lúc nào.

## 4. Đề xuất hướng phát triển

Nên phát triển thêm API riêng cho gia hạn, không nhét logic gia hạn vào API tạo mới.

Lý do:

* `POST /api/monthly-tickets` giữ đúng nghĩa là tạo vé mới.
* `POST /api/monthly-tickets/{id}/renew` thể hiện rõ đây là thao tác gia hạn.
* Frontend dễ làm UI: nút "Gia hạn" trên từng vé tháng.
* Backend dễ validate và tránh nhầm giữa tạo mới với gia hạn.

## 5. API đề xuất

### 5.1. Gia hạn theo ticket ID

```http
POST /api/monthly-tickets/{id}/renew
```

Request:

```json
{
  "months": 1,
  "note": "Khách gia hạn thêm 1 tháng"
}
```

Response: dùng lại `MonthlyTicketResponse`.

Ví dụ response:

```json
{
  "ticketId": 1,
  "vehicleId": 12,
  "licensePlate": "30A-12345",
  "parkingCardId": 5,
  "cardCode": "MC0001",
  "guestName": "Nguyễn Văn A",
  "guestPhone": "0987654321",
  "startDate": "2026-07-01T00:00:00",
  "endDate": "2026-08-31T23:59:59",
  "parkingBranchId": 2,
  "parkingBranchName": "Chi nhánh Cầu Giấy",
  "status": 1
}
```

### 5.2. Gia hạn theo xe hoặc thẻ

Có thể làm sau nếu frontend cần tìm nhanh bằng biển số/thẻ:

```http
POST /api/monthly-tickets/renew-by-card/{parkingCardId}
POST /api/monthly-tickets/renew-by-vehicle/{vehicleId}
```

Tuy nhiên giai đoạn đầu nên dùng `{id}/renew` để đơn giản và tránh gia hạn nhầm vé.

## 6. DTO cần thêm

Tạo file:

`src/main/java/Parking/dto/request/RenewMonthlyTicketRequest.java`

Gợi ý:

```java
package Parking.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RenewMonthlyTicketRequest {

    @NotNull(message = "Số tháng gia hạn là bắt buộc")
    @Min(value = 1, message = "Số tháng gia hạn tối thiểu là 1")
    @Max(value = 24, message = "Số tháng gia hạn tối đa là 24")
    private Integer months;

    private String note;
}
```

Ghi chú:

* `note` có thể chưa lưu DB nếu chưa có bảng lịch sử gia hạn.
* Nếu cần lưu lịch sử thanh toán/gia hạn, nên thêm bảng riêng ở giai đoạn sau.

## 7. Repository cần thêm

Trong `MonthlyTicketRepository`, nên thêm query tìm vé active/latest theo xe hoặc thẻ nếu cần:

```java
@Query("""
    SELECT mt FROM MonthlyTicket mt
    WHERE mt.ticketId = :ticketId
      AND mt.status = 1
""")
Optional<MonthlyTicket> findActiveByTicketId(@Param("ticketId") Long ticketId);
```

Nếu muốn tự tìm vé gần nhất theo xe:

```java
@Query("""
    SELECT mt FROM MonthlyTicket mt
    WHERE mt.vehicle.vehiclesId = :vehicleId
      AND mt.status = 1
    ORDER BY mt.endDate DESC
""")
List<MonthlyTicket> findActiveByVehicleOrderByEndDateDesc(@Param("vehicleId") Long vehicleId);
```

Nếu muốn tự tìm vé gần nhất theo thẻ:

```java
@Query("""
    SELECT mt FROM MonthlyTicket mt
    WHERE mt.parkingCard.parkingCardId = :parkingCardId
      AND mt.status = 1
    ORDER BY mt.endDate DESC
""")
List<MonthlyTicket> findActiveByCardOrderByEndDateDesc(@Param("parkingCardId") Long parkingCardId);
```

## 8. Service cần thêm

Trong `MonthlyTicketService`, thêm method:

```java
@Transactional
public MonthlyTicketResponse renewMonthlyTicket(Long id, RenewMonthlyTicketRequest request) {
    MonthlyTicket monthlyTicket = findMonthlyTicket(id);

    branchScopeService.assertSameBranch(
        monthlyTicket.getParkingCard().getParkingBranch().getParkingBranchId()
    );

    if (monthlyTicket.getStatus() == null || monthlyTicket.getStatus() != 1) {
        throw new ParkingSessionException("Chỉ có thể gia hạn vé tháng đang hoạt động");
    }

    if (request.getMonths() == null || request.getMonths() <= 0) {
        throw new ParkingSessionException("Số tháng gia hạn phải lớn hơn 0");
    }

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime baseTime = monthlyTicket.getEndDate().isAfter(now)
            ? monthlyTicket.getEndDate()
            : now;

    monthlyTicket.setEndDate(baseTime.plusMonths(request.getMonths()));

    return convertToResponse(monthlyTicketRepository.save(monthlyTicket));
}
```

Rule quan trọng:

* Nếu vé còn hạn: cộng từ `endDate` hiện tại.
* Nếu vé đã hết hạn nhưng vẫn cho gia hạn: cộng từ `now`.
* Nếu không muốn gia hạn vé đã hết hạn: đổi rule thành báo lỗi khi `endDate.isBefore(now)`.

## 9. Controller cần thêm

Trong `MonthlyTicketController`, thêm endpoint:

```java
@PostMapping("/{id}/renew")
@Operation(summary = "Gia hạn vé tháng")
@PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
public ResponseEntity<MonthlyTicketResponse> renewMonthlyTicket(
        @PathVariable Long id,
        @Valid @RequestBody RenewMonthlyTicketRequest request
) {
    return ResponseEntity.ok(monthlyTicketService.renewMonthlyTicket(id, request));
}
```

## 10. Rule tính thời gian đề xuất

### Rule A: Cộng theo tháng lịch

Code:

```java
monthlyTicket.setEndDate(baseTime.plusMonths(months));
```

Ưu điểm:

* Dễ hiểu.
* Dễ code.
* Phù hợp với gói "1 tháng", "3 tháng", "6 tháng".

Nhược điểm:

* Nếu vé bắt đầu ngày 31, cộng tháng có thể ra ngày cuối tháng tiếp theo tùy Java xử lý.

### Rule B: Cộng theo số ngày cố định

Code:

```java
monthlyTicket.setEndDate(baseTime.plusDays(30L * months));
```

Ưu điểm:

* Mỗi tháng tương đương đúng 30 ngày.
* Tránh vấn đề tháng 28/29/30/31 ngày.

Nhược điểm:

* Không đúng nghĩa "tháng lịch".

Đề xuất: dùng **Rule A** nếu sản phẩm bán theo tháng lịch; dùng **Rule B** nếu sản phẩm bán theo gói 30 ngày.

## 11. Các case cần test

### Case 1: Vé còn hạn, gia hạn 1 tháng

Input:

* `endDate = 2026-07-31T23:59:59`
* `months = 1`

Expected:

* `endDate` mới là khoảng `2026-08-31T23:59:59` nếu dùng `plusMonths(1)`.

### Case 2: Vé còn hạn, gia hạn 3 tháng

Input:

* `endDate = 2026-07-31T23:59:59`
* `months = 3`

Expected:

* `endDate` mới là khoảng `2026-10-31T23:59:59`.

### Case 3: Vé đã hết hạn, vẫn cho gia hạn

Input:

* `endDate < now`
* `months = 1`

Expected:

* Nếu chọn rule cho gia hạn vé hết hạn: `endDate = now.plusMonths(1)`.
* Nếu không cho gia hạn vé hết hạn: API trả lỗi.

### Case 4: User khác branch gia hạn

Input:

* STAFF/MANAGER thuộc branch A.
* Vé tháng thuộc branch B.

Expected:

* API trả lỗi không có quyền thao tác branch này.

### Case 5: Vé bị khóa

Input:

* `status = 0`

Expected:

* API trả lỗi: chỉ có thể gia hạn vé tháng đang hoạt động.

## 12. Tác động frontend

Frontend nên thêm nút **Gia hạn** ở màn hình danh sách/chi tiết vé tháng.

Luồng UI:

1. Nhân viên chọn vé tháng.
2. Bấm "Gia hạn".
3. Chọn số tháng: 1, 3, 6, 12.
4. Gọi `POST /api/monthly-tickets/{id}/renew`.
5. Hiển thị `endDate` mới sau khi API trả về.

Không nên để frontend tự tính `endDate` rồi gọi API update, vì rule gia hạn nên nằm ở backend để tránh sai lệch.

## 13. Tác động check-in/check-out

Hiện `ParkingSessionService` và `PaymentService` đang kiểm tra vé tháng active theo thời điểm hiện tại.

Sau khi gia hạn bằng cách cập nhật `endDate`, các luồng sau sẽ tự hưởng lợi:

* Check-in thẻ tháng: vé tiếp tục được xem là còn hạn.
* Check-out thẻ tháng: nếu còn hạn thì amount bằng `0`.

Không cần sửa check-in/check-out nếu chỉ gia hạn bằng cách kéo dài `endDate`.

## 14. Có cần bảng lịch sử gia hạn không?

Giai đoạn đầu có thể chưa cần.

Nhưng nếu cần quản lý doanh thu/giao dịch rõ ràng, nên thêm bảng:

`monthly_ticket_renewal`

Gợi ý field:

* `renewal_id`
* `ticket_id`
* `old_end_date`
* `new_end_date`
* `months`
* `amount`
* `payment_method`
* `created_by`
* `created_at`
* `note`

Lý do nên có bảng này về sau:

* Biết mỗi lần khách gia hạn bao nhiêu tháng.
* Đối soát tiền.
* Audit khi nhân viên sửa nhầm `endDate`.
* Làm báo cáo doanh thu vé tháng.

## 15. Checklist triển khai

1. Tạo `RenewMonthlyTicketRequest`.
2. Thêm method `renewMonthlyTicket` trong `MonthlyTicketService`.
3. Thêm endpoint `POST /api/monthly-tickets/{id}/renew`.
4. Validate branch scope giống các API monthly ticket hiện tại.
5. Chọn rule cộng thời gian: `plusMonths` hoặc `plusDays(30 * months)`.
6. Test case vé còn hạn, hết hạn, khác branch, status khóa.
7. Cập nhật tài liệu frontend nếu endpoint được triển khai.

