# Kiểm tra luồng VNPAY khi checkout thất bại

## Kết luận ngắn

- Thanh toán VNPAY thất bại **không hoàn tất checkout**.
- `ParkingSession` vẫn giữ trạng thái `ACTIVE` và thẻ xe chưa được chuyển về `AVAILABLE`.
- Người dùng có thể gọi lại API checkout với phương thức `VNPAY` để thanh toán lại.
- Khi thanh toán lại, backend tái sử dụng bản ghi `Payment` cũ nhưng tạo `transactionRef` mới và đưa trạng thái về `PENDING`.
- Callback thất bại hiện chỉ trả thông báo `Thanh toán thất bại.`; chưa có câu hướng dẫn rõ ràng `Vui lòng thanh toán lại`.

Vì vậy, về phía backend, xe **không được phép checkout ra khi VNPAY thất bại**. Tuy nhiên frontend cần dựa vào `success=false` hoặc `paymentStatus=FAILED` để hiển thị nút/thông báo thanh toán lại.

## Luồng checkout ban đầu

Endpoint:

```http
POST /api/parking-sessions/guest/check-out
```

`ParkingSessionService.guestCheckOut()` thực hiện:

1. Tìm phiên gửi xe có trạng thái `ACTIVE` bằng mã thẻ.
2. Kiểm tra quyền theo chi nhánh.
3. So sánh biển số lúc ra với biển số lúc vào.
4. Chuyển xử lý sang `PaymentService.processCheckOutPayment()`.

Khi chọn `VNPAY`, `PaymentService`:

1. Tạo hoặc tái sử dụng bản ghi `Payment` chưa thanh toán thành công.
2. Tính phí gửi xe và phí phạt mất thẻ nếu có.
3. Tạo `transactionRef` mới.
4. Đặt `Payment.paymentStatus = PENDING`.
5. Giữ `ParkingSession.status = ACTIVE`.
6. Trả `paymentUrl` và thông báo yêu cầu thanh toán qua VNPAY.

Lưu ý: code đã gán `checkOutTime` trước khi thanh toán thành công, dù session vẫn `ACTIVE`. Đây là dữ liệu thời điểm dự kiến checkout, chưa phải bằng chứng rằng xe đã checkout hoàn tất.

## Khi VNPAY thanh toán thành công

Khi `vnp_ResponseCode = 00` và chữ ký, số tiền đều hợp lệ:

- `Payment.paymentStatus` chuyển thành `PAID`.
- `Payment.paidAt` được ghi nhận.
- `ParkingSession.status` chuyển thành `COMPLETED`.
- Thẻ xe chuyển thành `AVAILABLE`, trừ trường hợp thẻ đã báo mất.
- Return response có `success=true`.
- Frontend được redirect về `/staff/exit` với thông báo thanh toán thành công.

Chỉ tại bước này xe mới được xem là đã checkout hoàn tất.

## Khi VNPAY thanh toán thất bại

Khi `vnp_ResponseCode` khác `00` hoặc số tiền không khớp:

- `Payment.paymentStatus` chuyển thành `FAILED`.
- `ParkingSession.status` không bị đổi sang `COMPLETED`, nên vẫn là `ACTIVE`.
- Thẻ xe không được đổi về `AVAILABLE`.
- Return response có `success=false`.
- Frontend được redirect về `/staff/exit` với query parameter:

```text
success=false
message=Thanh toán thất bại.
```

Do đó thanh toán thất bại không tự động cho xe ra khỏi bãi.

## Có thanh toán lại được không?

Có. Nhân viên có thể gửi lại request checkout cho cùng mã thẻ và biển số.

Vì session vẫn `ACTIVE`, hệ thống vẫn tìm thấy phiên gửi xe. Trong `processCheckOutPayment()`:

- Nếu payment cũ là `PAID`, hệ thống chặn để tránh thanh toán hai lần.
- Nếu payment cũ là `FAILED` hoặc `PENDING`, hệ thống tái sử dụng payment đó.
- Backend tạo `transactionRef` mới.
- Xóa dữ liệu phản hồi VNPAY của lần trước.
- Đặt lại trạng thái payment thành `PENDING`.
- Sinh một `paymentUrl` mới.

Như vậy backend đã có cơ chế retry, nhưng frontend phải gọi lại API checkout để nhận URL mới; URL của giao dịch thất bại không nên được tái sử dụng.

## Điểm chưa tốt và rủi ro

### 1. Thông báo thất bại chưa hướng dẫn thanh toán lại

Thông báo hiện tại:

```text
Thanh toán thất bại.
```

Nên đổi thành:

```text
Thanh toán thất bại. Phiên gửi xe chưa hoàn tất, vui lòng thực hiện thanh toán lại.
```

Frontend nên hiển thị nút `Thanh toán lại`, sau đó gọi lại endpoint checkout để lấy `paymentUrl` mới.

### 2. `checkOutTime` được ghi trước khi thanh toán thành công

Trong nhánh VNPAY, `checkOutTime` và `totalAmount` được lưu ngay khi tạo URL, trong khi session vẫn `ACTIVE`. Nếu người dùng thanh toán thất bại rồi retry muộn hơn, lần retry sẽ tính phí lại và ghi đè `checkOutTime` mới. Trạng thái hiện tại vẫn ngăn checkout sai, nhưng tên và thời điểm dữ liệu có thể gây hiểu nhầm khi báo cáo.

### 3. Return URL và IPN cùng cập nhật payment

Cả `handleVnPayCallback()` và `handleVnPayIpn()` đều có thể cập nhật trạng thái thanh toán. Có một tình huống cần lưu ý:

- Nếu Return xử lý thất bại trước, payment chuyển thành `FAILED`.
- IPN đến sau sẽ coi cả `PAID` và `FAILED` là giao dịch đã được xác nhận và trả mã `02`.

Điều này khiến `FAILED` bị coi như trạng thái kết thúc đối với IPN. Nên thiết kế một hàm xử lý kết quả thanh toán dùng chung cho Return và IPN, đồng thời quy định rõ nguồn nào có quyền chốt trạng thái cuối cùng.

### 4. Response IPN còn thông báo tiếng Anh

Hai thông báo IPN vẫn đang là:

```text
Incorrect Merchant TMN Code
Confirm Success
```

Đây là phản hồi giữa backend và VNPAY, không phải thông báo trực tiếp cho người dùng. Nếu muốn đồng bộ tiếng Việt toàn hệ thống có thể dịch, nhưng cần giữ nguyên `RspCode` theo giao thức VNPAY.

## Yêu cầu frontend đề xuất

Frontend tại trang `/staff/exit` nên đọc các query parameter do backend redirect về:

- `success`
- `paymentType`
- `transactionRef`
- `responseCode`
- `message`

Xử lý đề xuất:

```text
success=true
  -> thông báo thanh toán thành công
  -> làm mới thông tin phiên gửi xe
  -> chỉ cho phép mở barrier khi session đã là COMPLETED

success=false
  -> thông báo phiên gửi xe chưa hoàn tất
  -> giữ xe tại cổng
  -> hiển thị nút Thanh toán lại
  -> gọi lại API guest/check-out để lấy paymentUrl mới
```

## Kết luận nghiệp vụ

Điều kiện cho xe ra không nên chỉ dựa trên việc trình duyệt đã quay về từ VNPAY. Hệ thống phải kiểm tra đồng thời:

```text
Payment.paymentStatus == PAID
ParkingSession.status == COMPLETED
```

Với code hiện tại, thanh toán thất bại vẫn giữ session `ACTIVE`, nên về mặt backend xe chưa được checkout. Cơ chế thanh toán lại đã tồn tại, nhưng thông báo và giao diện cần làm rõ để nhân viên biết phải tạo một lượt thanh toán VNPAY mới.
