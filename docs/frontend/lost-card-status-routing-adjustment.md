# Điều chỉnh frontend: trạng thái báo mất thẻ

## Phân nhánh theo loại thẻ

Frontend phải lấy `cardType` và `status` từ response backend. Không tự suy ra loại thẻ từ tiêu đề hoặc mã thẻ.

### User báo mất thẻ tháng

Sau khi gọi:

```http
POST /api/incidents/lost-card
```

Nếu thẻ là `MONTHLY` và người đăng nhập là `USER`, response phải hiển thị ngay:

```text
Trạng thái: Chờ thanh toán
Phí mất thẻ: 50.000 VND
```

Không hiển thị bước “Chờ xác minh”. User được chọn một trong hai cách thanh toán:

- VNPay: `POST /api/incidents/{id}/lost-card-payment`.
- Tiền mặt tại quầy: staff gọi `POST /api/incidents/{id}/lost-card-payment/cash`.

Sau khi staff ghi nhận tiền mặt, payment là `PAID`, hiển thị “Đã thanh toán”, ẩn VNPay và bật luồng cấp thẻ cho manager.

### Guest/thẻ khách báo mất

Nếu `cardType = REGULAR` hoặc không có vé tháng:

- Chỉ gửi report về manager.
- Hiển thị trạng thái “Chờ manager duyệt”.
- Không hiển thị phí 50.000 VND.
- Không hiển thị nút VNPay.
- Không hiển thị nút thanh toán tiền mặt.
- Không hiển thị nút cấp thẻ tháng mới.

## Màn hình manager

### Guest/thẻ khách

Với report `REGULAR` ở trạng thái `PENDING`, hiển thị hai nút:

```text
Duyệt
Hủy
```

Nút **Duyệt** gọi:

```http
PUT /api/incidents/{id}/verify-lost-card
```

Không mở form nhập số tiền hoặc số biên lai. Sau khi thành công, tải lại report và hiển thị `IN_PROGRESS`/“Đã duyệt”.

Nút **Hủy** gọi:

```http
PUT /api/incidents/{id}/cancel
```

Sau khi hủy, đóng modal, tải lại danh sách và ẩn toàn bộ thao tác tiếp theo.

### Thẻ tháng đã thanh toán

Nếu response có:

```json
{
  "cardType": "MONTHLY",
  "paymentStatus": "PAID",
  "status": "IN_PROGRESS"
}
```

Frontend tự điền thông tin payment dạng chỉ đọc, không yêu cầu manager nhập số tiền. Hiển thị nút **Cấp thẻ mới**.

Nếu payment là `PAID`, manager không bấm xác minh payment nữa; frontend chỉ hiển thị thông tin thanh toán dạng read-only và nút **Cấp thẻ mới**.

## Quy tắc hiển thị

```javascript
if (cardType === "MONTHLY" && role === "USER") {
  // report mới: WAITING_PAYMENT, hiển thị thanh toán
}

if (cardType === "REGULAR" && status === "PENDING") {
  // manager: chỉ hiển thị Duyệt/Hủy
}

if (paymentMethod === "CASH" && paymentStatus === "PAID") {
  // đã thu và xác nhận tại quầy; manager chỉ cấp thẻ mới
}

if (paymentStatus === "PAID" && cardType === "MONTHLY") {
  // manager: hiển thị Cấp thẻ mới
}
```

Frontend luôn gọi lại `GET /api/incidents/{id}` sau mỗi thao tác và dùng `status`, `cardType`, `paymentStatus` từ backend làm nguồn dữ liệu cuối cùng.
