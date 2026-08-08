# Báo cáo điều chỉnh luồng báo mất thẻ tháng

## 1. Hiện trạng

Khi user báo mất thẻ tháng, frontend đang hiển thị:

- `status = PENDING`.
- Phí mất thẻ là `0 VND`.
- Chưa có nút thanh toán VNPay.
- Manager chưa thể cấp thẻ mới vì backend yêu cầu payment phải `PAID`.

Kết quả là luồng bị kẹt:

```text
PENDING → chưa có payment → chưa cấp được thẻ mới
```

Nguyên nhân là hệ thống chưa có bước xác minh/duyệt rõ ràng giữa lúc user gửi báo cáo và lúc tạo khoản phải thu.

## 2. Luồng nghiệp vụ điều chỉnh

```text
User báo mất thẻ
        ↓
PENDING
        ↓ Manager xác minh
WAITING_PAYMENT, phí = 50.000 VND
        ↓ User thanh toán VNPay
Payment = PAID
        ↓
Manager cấp thẻ tháng mới
        ↓
Manager hoàn tất báo cáo
RESOLVED
```

### Xe đã vào bãi

```text
Thanh toán phí mất thẻ
→ Checkout và thanh toán phí gửi xe riêng
→ Session không còn ACTIVE
→ Manager cấp thẻ mới
→ Resolve báo cáo
```

### Xe chưa vào bãi

```text
Thanh toán phí mất thẻ
→ Manager cấp thẻ mới
→ Resolve báo cáo
```

Trường hợp xe chưa vào bãi không được chạy checkout, không tính phí lưu xe và không mở barrier.

## 3. Điều chỉnh backend

### Bước xác minh

Tạo hoặc điều chỉnh API:

```http
PUT /api/incidents/{id}/verify-lost-card
```

Khi manager xác minh hợp lệ:

```java
report.setStatus(IncidentStatus.WAITING_PAYMENT);
report.setLostCardFee(new BigDecimal("50000"));
```

Backend cần kiểm tra incident là `LOST_CARD`, user sở hữu thẻ và dữ liệu xe/thẻ hợp lệ.

### Tạo payment

```http
POST /api/incidents/{id}/lost-card-payment
```

Chỉ cho phép khi incident ở `WAITING_PAYMENT`. Backend tự cố định số tiền `50.000 VND`, không nhận số tiền do frontend gửi.

### Nhận kết quả VNPay

Chỉ IPN hợp lệ mới được cập nhật:

```java
payment.setPaymentStatus(PaymentStatus.PAID);
```

Frontend redirect không được dùng làm bằng chứng thanh toán.

### Cấp thẻ thay thế

Trong `LostMonthlyCardReplacementService`, bắt buộc payment đã thành công:

```java
if (incident.getPayment() == null
        || incident.getPayment().getPaymentStatus() != PaymentStatus.PAID) {
    throw new InvalidTicketStateException(
        "User chưa thanh toán phí mất thẻ, chưa thể cấp thẻ thay thế");
}
```

Nếu `parkingSession == null`, bỏ qua toàn bộ logic checkout. Nếu session còn `ACTIVE`, phải checkout trước khi cấp thẻ mới.

### Hoàn tất báo cáo

`resolveIncident()` chỉ cho phép `RESOLVED` khi:

- Payment có trạng thái `PAID`.
- Thẻ mới đã được cấp đối với thẻ tháng.
- Nếu xe ở trong bãi, session đã checkout.

## 4. Điều chỉnh frontend

### `PENDING`

Hiển thị:

```text
Báo cáo đang chờ manager xác minh.
Phí mất thẻ sẽ được hiển thị sau khi xác minh.
```

Không hiển thị `0 VND` như phí cuối cùng và không hiển thị nút thanh toán.

### `WAITING_PAYMENT`

Hiển thị:

```text
Phí mất thẻ: 50.000 VND
```

Cho phép user bấm **Thanh toán qua VNPay**.

### `PAID`

Hiển thị:

```text
Đã thanh toán phí mất thẻ.
Đang chờ manager cấp thẻ mới.
```

Manager mới được thấy nút **Cấp thẻ mới**.

### Xe chưa vào bãi

Ẩn các chức năng:

- Checkout.
- Phí gửi xe.
- Mở barrier.
- Đưa xe ra bãi.

Chỉ hiển thị chuỗi thao tác thanh toán, cấp thẻ mới và hoàn tất báo cáo.

## 5. Acceptance criteria

- Báo cáo mới không hiển thị `0 VND` như phí cuối cùng.
- Manager có thể xác minh báo cáo `PENDING`.
- Sau xác minh, incident chuyển sang `WAITING_PAYMENT` và phí là `50.000 VND`.
- User tạo được URL VNPay khi incident `WAITING_PAYMENT`.
- Payment chỉ chuyển `PAID` sau IPN hợp lệ.
- Không thể cấp thẻ mới nếu payment chưa `PAID`.
- Xe chưa vào bãi không bị yêu cầu checkout hoặc mở barrier.
- Chỉ resolve sau khi payment, cấp thẻ và checkout (nếu có) hoàn tất.

