# Chỉnh sửa luồng trả về VNPay cho Monthly Ticket và Staff Checkout

## 1. Kết luận kiểm tra backend

Backend hiện sử dụng chung callback sau cho cả hai loại thanh toán VNPay:

```text
GET /api/payments/vnpay-return
```

URL callback được nhúng vào `paymentUrl` thông qua tham số `vnp_ReturnUrl` trong:

```text
src/main/java/Parking/Service/VnPayService.java
```

Giá trị `vnp_ReturnUrl` lấy từ cấu hình:

```properties
vnpay.return-url=${VNPAY_RETURN_URL:https://bullpen-viewer-overfill.ngrok-free.dev/api/payments/vnpay-return}
```

Việc để VNPay quay lại backend trước là đúng. Backend cần xác thực chữ ký, cập nhật trạng thái giao dịch, sau đó trả HTTP 302 để chuyển trình duyệt sang frontend.

## 2. Luồng hiện tại

Trong `PaymentService.handleVnPayCallback`, backend phân loại giao dịch như sau:

```java
String paymentType = payment.getMonthlyTicketRequest() != null
        ? "MONTHLY_TICKET"
        : "PARKING_SESSION";
```

Trong `PaymentController.vnPayReturn`, trang frontend đích đang được chọn như sau:

```java
String targetPath = "MONTHLY_TICKET".equals(response.getPaymentType())
        ? "/user-dashboard"
        : "/payment-result";
```

Kết quả hiện tại:

| Loại thanh toán | `paymentType` | Trang frontend được chuyển đến |
|---|---|---|
| Đăng ký thẻ tháng | `MONTHLY_TICKET` | `/user-dashboard` |
| Thanh toán checkout do staff thực hiện | `PARKING_SESSION` | `/payment-result` |

Như vậy:

- Luồng staff checkout đã chuyển đúng tới `PaymentResultPage`.
- Luồng monthly ticket chưa chuyển tới `PaymentResultPage`; nó đang được chuyển về `user-dashboard`.
- Backend không chuyển thẳng VNPay tới frontend. VNPay quay lại callback backend, sau đó backend mới redirect tới frontend.

## 3. Thay đổi cần thực hiện

Nếu yêu cầu là cả monthly ticket và staff checkout đều hiển thị `PaymentResultPage`, sửa đoạn chọn `targetPath` trong:

```text
src/main/java/Parking/Controller/PaymentController.java
```

### Mã hiện tại

```java
String targetPath = "MONTHLY_TICKET".equals(response.getPaymentType())
        ? "/user-dashboard"
        : "/payment-result";
```

### Mã đề xuất

```java
String targetPath = "/payment-result";
```

URL redirect cuối cùng sẽ có dạng:

```text
https://ten-mien-frontend/payment-result?success=true&message=Thanh+toán+thành+công
```

Khi giao dịch thất bại:

```text
https://ten-mien-frontend/payment-result?success=false&message=Thanh+toán+không+thành+công
```

## 4. Cấu hình URL frontend

`PaymentController` đang đọc cấu hình sau:

```java
@Value("${app.frontend.url:http://localhost:5173}")
private String frontendUrl;
```

Tuy nhiên, `application.properties` hiện chưa khai báo `app.frontend.url`. Nếu môi trường triển khai không truyền giá trị này, backend sẽ redirect về `http://localhost:5173`.

Thêm vào `src/main/resources/application.properties`:

```properties
# URL frontend dùng cho redirect sau khi backend xử lý VNPay Return URL
app.frontend.url=${APP_FRONTEND_URL:http://localhost:5173}
```

Cấu hình đề xuất khi triển khai:

```env
APP_FRONTEND_URL=https://ten-mien-frontend
VNPAY_RETURN_URL=https://ten-mien-backend/api/payments/vnpay-return
VNPAY_IPN_URL=https://ten-mien-backend/api/payments/vnpay-ipn
```

Không đặt `VNPAY_RETURN_URL` trực tiếp thành `/payment-result`, vì frontend không thực hiện xác thực chữ ký VNPay và cập nhật trạng thái `Payment`.

## 5. Luồng đúng sau khi chỉnh sửa

```text
Frontend
  -> gọi API tạo thanh toán monthly hoặc checkout
Backend
  -> tạo Payment trạng thái PENDING
  -> tạo paymentUrl có vnp_ReturnUrl trỏ về backend
Frontend
  -> mở paymentUrl
VNPay
  -> xử lý thanh toán
  -> chuyển trình duyệt về /api/payments/vnpay-return
Backend callback
  -> xác thực chữ ký và TMN code
  -> tìm Payment bằng vnp_TxnRef
  -> cập nhật Payment PAID hoặc FAILED
  -> cập nhật MonthlyTicketRequest hoặc ParkingSession
  -> trả HTTP 302 tới frontend /payment-result
Frontend PaymentResultPage
  -> đọc success và message trên query string
  -> hiển thị kết quả giao dịch
```

## 6. Các trường hợp cần kiểm thử

### Monthly ticket thành công

- Gọi `POST /api/monthly-ticket-requests/{id}/payment`.
- Kiểm tra response có `paymentUrl`.
- Thanh toán VNPay thành công.
- Kiểm tra trình duyệt vào `/payment-result?success=true...`.
- Kiểm tra `Payment.paymentStatus = PAID`.
- Kiểm tra `MonthlyTicketRequest.status = 1`.

### Monthly ticket thất bại hoặc bị hủy

- Hủy giao dịch trên VNPay.
- Kiểm tra trình duyệt vào `/payment-result?success=false...`.
- Kiểm tra `Payment.paymentStatus = FAILED`.

### Staff checkout thành công

- Staff thực hiện checkout với phương thức `VNPAY`.
- Thanh toán thành công.
- Kiểm tra trình duyệt vào `/payment-result?success=true...`.
- Kiểm tra `Payment.paymentStatus = PAID`.
- Kiểm tra `ParkingSession.status = COMPLETED`.
- Kiểm tra thẻ được trả về `AVAILABLE`, trừ trường hợp thẻ đã bị đánh dấu `LOST`.

### Cấu hình production

- Kiểm tra `APP_FRONTEND_URL` không phải `localhost`.
- Kiểm tra `VNPAY_RETURN_URL` là URL HTTPS công khai của backend.
- Kiểm tra callback và IPN được phép truy cập không cần JWT.
- Kiểm tra URL redirect không xuất hiện dấu `/` kép khi `APP_FRONTEND_URL` có dấu `/` ở cuối.

## 7. Gợi ý tránh dấu `/` kép

Nên chuẩn hóa `frontendUrl` trước khi ghép đường dẫn:

```java
String normalizedFrontendUrl = frontendUrl.endsWith("/")
        ? frontendUrl.substring(0, frontendUrl.length() - 1)
        : frontendUrl;

String redirectUrl = normalizedFrontendUrl + "/payment-result"
        + "?success=" + response.isSuccess()
        + "&message=" + URLEncoder.encode(
                response.getMessage() != null ? response.getMessage() : "",
                StandardCharsets.UTF_8
        );
```

## 8. Phạm vi chỉnh sửa tối thiểu

Hai thay đổi tối thiểu cần áp dụng:

1. Đổi trang đích monthly từ `/user-dashboard` thành `/payment-result` trong `PaymentController`.
2. Khai báo `app.frontend.url=${APP_FRONTEND_URL:http://localhost:5173}` trong `application.properties` và đặt `APP_FRONTEND_URL` đúng trên môi trường triển khai.

