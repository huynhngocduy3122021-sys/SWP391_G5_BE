# Review phương pháp triển khai Booking Backend

File được review: `docs/09_BOOKING_METHODOLOGY.md`

## Kết luận nhanh

Phương pháp hiện tại đi đúng hướng ở mức nghiệp vụ: có bảng `booking`, có trạng thái vòng đời, có API tạo/hủy/xem booking, có scheduler để hết hạn booking, và có nêu rule quan trọng là chỉ cho phép Ô tô / Ô tô điện được đặt trước.

Tuy nhiên nếu đem code ngay theo tài liệu hiện tại thì vẫn còn một số rủi ro lớn: kiểm tra loại xe bằng ID hoặc keyword chưa ổn định, chưa mô tả cách chống overbooking khi nhiều request cùng lúc, chưa khớp hoàn toàn với cách repo hiện tại đang tính sức chứa bằng `ParkingZone.capacity` và `ParkingSession.ACTIVE`, và chưa rõ cách liên kết booking với check-in thực tế.

## Những điểm đang ổn

1. Tách `Booking` thành một entity riêng là hợp lý.
   Booking có vòng đời khác `ParkingSession`: booking là giữ chỗ trong tương lai, còn session là xe đã vào bãi. Không nên gộp hai khái niệm này vào một bảng.

2. Dùng enum/string status cho booking là đúng hướng.
   Các trạng thái `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `EXPIRED` đủ để mô tả luồng cơ bản.

3. Có scheduler expire booking là cần thiết.
   Nếu không có tác vụ tự động hết hạn, slot đã giữ sẽ bị treo và làm sai số lượng chỗ trống.

4. API cơ bản tương đối đủ cho MVP.
   `POST /api/bookings`, `POST /api/bookings/{id}/cancel`, `GET /api/bookings/my-bookings` là bộ endpoint hợp lý để bắt đầu.

## Những điểm nên chỉnh trước khi implement

### 1. Không nên validate loại xe bằng ID cố định hoặc keyword tự do

Tài liệu đang đề xuất cho phép ID `{3, 4}` hoặc kiểm tra `typeName` chứa chữ liên quan đến ô tô. Hai cách này đều dễ lỗi:

- ID seed data có thể thay đổi giữa database local, test, production.
- `typeName` có tiếng Việt dấu/không dấu, viết hoa/thường, hoặc tên nhập sai như `Oto`, `Xe hơi`, `Ô tô điện`.
- Keyword có thể nhận nhầm nếu dữ liệu không chuẩn.

Đề xuất tốt hơn:

- Thêm một field ổn định vào `VehicleType`, ví dụ `code` hoặc enum category.
- Ví dụ: `CAR`, `ELECTRIC_CAR`, `MOTORBIKE`, `ELECTRIC_MOTORBIKE`.
- Booking chỉ cho phép `CAR` và `ELECTRIC_CAR`.

Nếu chưa muốn sửa schema nhiều, tối thiểu nên tạo helper/service trung tâm:

```java
private boolean isBookableVehicleType(VehicleType vehicleType) {
    return Set.of("CAR", "ELECTRIC_CAR").contains(vehicleType.getCode());
}
```

Không nên rải logic kiểm tra này ở controller hoặc nhiều service khác nhau.

### 2. Cần thiết kế rõ cách giữ chỗ để tránh overbooking

Repo hiện tại trong `ParkingSessionService.guestCheckIn` đang tính chỗ trống theo công thức:

```text
totalCapacity = SUM(ParkingZone.capacity theo branch + vehicleType)
currentVehicleCount = COUNT(ParkingSession ACTIVE theo branch + vehicleType)
```

Nếu thêm booking, công thức tạo booking nên tính cả booking đang giữ chỗ:

```text
available = totalCapacity - activeSessions - confirmedBookingsInHoldWindow
```

Trong đó `confirmedBookingsInHoldWindow` là các booking trạng thái `CONFIRMED` chưa hết hạn và có thể chiếm chỗ tại thời điểm dự kiến đến.

Nếu chỉ kiểm tra `ParkingSession.ACTIVE`, hệ thống có thể nhận quá nhiều booking vì các xe đã booking chưa vào bãi nên chưa được tính vào số xe đang đỗ.

### 3. Cần chống race condition khi nhiều người đặt cùng lúc

Luồng booking có rủi ro hai request cùng đọc thấy còn 1 slot và cùng tạo `CONFIRMED`. Tài liệu nên bổ sung một trong các hướng:

- Dùng transaction với pessimistic lock trên `ParkingBranch` hoặc các `ParkingZone` liên quan khi kiểm tra capacity và tạo booking.
- Hoặc tạo bảng/record inventory theo branch + vehicle type + time window rồi update bằng điều kiện nguyên tử.
- Với MVP, có thể dùng `@Transactional` + repository query `@Lock(PESSIMISTIC_WRITE)` cho branch/zone trước khi đếm và lưu booking.

Nếu không xử lý phần này, bug overbooking sẽ rất khó phát hiện khi test thủ công nhưng dễ xảy ra khi chạy thật.

### 4. Cần thống nhất booking gắn với `vehicle_id` như thế nào

Tài liệu để `vehicle_id` nullable vì khách có thể chưa đăng ký biển số. Nhưng phần check-in lại nói tìm booking theo `licensePlate`.

Cần chọn một trong hai hướng rõ ràng:

- Bắt buộc nhập biển số khi booking, tạo hoặc liên kết `Vehicle` ngay lúc booking. Cách này dễ match khi check-in.
- Hoặc cho phép chưa có biển số, nhưng lúc check-in phải match bằng mã booking/QR/user, không thể chỉ dựa vào license plate.

Với hệ thống hiện tại đã có `Vehicle` và `ParkingSession` dựa trên biển số, hướng đơn giản nhất là yêu cầu `licensePlate` khi tạo booking hoặc yêu cầu `vehicleId` thuộc user.

### 5. Nên liên kết `Booking` với `ParkingSession`

Tài liệu nói khi check-in thì chuyển booking thành `COMPLETED` và tạo `ParkingSession`, nhưng schema chưa có quan hệ rõ.

Nên thêm một trong hai cách:

- `booking.parking_session_id` nullable, one-to-one tới `parking_session`.
- Hoặc `parking_session.booking_id` nullable.

Quan hệ này giúp audit được booking nào đã tạo ra phiên gửi xe nào, tránh mất trace khi kiểm tra thanh toán, khiếu nại hoặc báo cáo.

### 6. Cần bổ sung rule về thời gian đặt

Tài liệu mới có `expected_arrival_time`, nhưng chưa có các rule quan trọng:

- Không cho đặt trong quá khứ.
- Có giới hạn đặt trước tối thiểu/tối đa, ví dụ sớm nhất sau 15 phút và xa nhất 7 ngày.
- Có grace period, ví dụ giữ chỗ đến `expectedArrivalTime + 30 phút`.
- Có quy định mỗi user/vehicle được có bao nhiêu booking active cùng lúc.

Nên thêm field:

```text
hold_until
cancelled_at
completed_at
expired_at
created_at
updated_at
```

`hold_until` đặc biệt quan trọng vì scheduler có thể expire theo field này thay vì tự tính lại từ `expected_arrival_time`.

### 7. Cần kiểm tra quyền user

API `GET /api/bookings/my-bookings` và cancel booking cần lấy user từ JWT/security context, không nên tin `userId` truyền từ request.

Khi hủy booking:

- User thường chỉ được hủy booking của chính mình.
- Admin/staff có thể hủy booking trong chi nhánh, nếu hệ thống có phân quyền này.

### 8. Nên trả lỗi nghiệp vụ theo exception hiện có

Repo hiện tại đang dùng `ParkingSessionException` cho nhiều lỗi nghiệp vụ. Khi thêm booking, nên tạo `BookingException` riêng nếu có global exception handler hỗ trợ, hoặc dùng một exception nghiệp vụ chung có mapping HTTP rõ ràng.

Không nên throw exception tùy ý ở controller. Validation và nghiệp vụ nên nằm trong `BookingService`.

## Đề xuất cấu trúc code

Nên triển khai theo cấu trúc hiện có của repo:

```text
Parking/Model/Booking.java
Parking/enums/BookingStatus.java
Parking/Repository/BookingRepository.java
Parking/Service/BookingService.java
Parking/Controller/BookingController.java
Parking/dto/request/CreateBookingRequest.java
Parking/dto/response/BookingResponse.java
Parking/exception/exceptions/BookingException.java
```

Các trách nhiệm chính:

- `BookingController`: nhận request, lấy user hiện tại, gọi service.
- `BookingService`: validate vehicle type, kiểm tra capacity, tạo/hủy/expire booking.
- `BookingRepository`: query theo user, status, branch, vehicle type, hold window.
- `ParkingSessionService`: khi check-in, tìm booking phù hợp rồi link sang `ParkingSession`.

## Gợi ý schema Booking chỉnh lại

```text
booking_id
user_id
parking_branch_id
vehicle_id
vehicle_type_id
parking_session_id nullable
expected_arrival_time
hold_until
status
deposit_amount nullable
created_at
updated_at
cancelled_at nullable
completed_at nullable
expired_at nullable
```

Index nên có:

```text
(user_id, status)
(parking_branch_id, vehicle_type_id, status, hold_until)
(vehicle_id, status)
```

Nếu dùng SQL Server và JPA, nên giữ tên bảng/cột theo style hiện tại của repo: snake_case trong database, camelCase trong Java.

## Checklist nên bổ sung vào tài liệu gốc

1. Thay validate bằng ID/keyword thành validate bằng `VehicleType.code` hoặc enum/category ổn định.
2. Bổ sung công thức capacity có tính cả booking đang giữ chỗ.
3. Bổ sung transaction/lock để chống overbooking.
4. Quy định rõ booking có bắt buộc biển số/vehicle không.
5. Thêm quan hệ giữa `Booking` và `ParkingSession`.
6. Thêm rule thời gian: không đặt quá khứ, giới hạn đặt trước, `hold_until`, grace period.
7. Bổ sung rule phân quyền user/admin khi xem và hủy booking.
8. Bổ sung scheduler expire theo `hold_until`.
9. Bổ sung test case cho car/electric car được booking, motorbike/electric motorbike bị chặn, hết slot, cancel, expire, check-in từ booking.

## Đánh giá cuối

Tài liệu `09_BOOKING_METHODOLOGY.md` ổn để mô tả ý tưởng nghiệp vụ, nhưng chưa đủ chặt để làm blueprint code production. Nên chỉnh theo các điểm trên trước khi implement, đặc biệt là phần loại xe, chống overbooking, và liên kết booking với check-in.
