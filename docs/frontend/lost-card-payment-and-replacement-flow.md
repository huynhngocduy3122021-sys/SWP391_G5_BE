# Luồng frontend: thanh toán và cấp lại thẻ tháng bị mất

> **Luồng chuẩn mới:** xem [unified-lost-monthly-card-flow.md](./unified-lost-monthly-card-flow.md). File này giữ tham chiếu tương thích; không dùng các bước payment cũ làm luồng chính nếu triển khai theo yêu cầu thống nhất mới.

Tài liệu này mô tả luồng frontend phải bám theo backend hiện tại. Frontend không tự đổi trạng thái incident, không tự xác nhận thanh toán và không tự tính phí.

## Luồng tổng quát

```text
User báo mất thẻ
       ↓
Manager/Staff tiếp nhận và xác minh
       ↓
Incident = IN_PROGRESS
       ↓
Tạo payment phí mất thẻ 50.000 VND
       ↓
Incident = WAITING_PAYMENT
       ↓
User thanh toán VNPay
       ↓
Backend nhận IPN hợp lệ: Payment = PAID
       ↓
Incident = IN_PROGRESS
       ↓
Manager cấp thẻ tháng mới
       ↓
Manager resolve incident
       ↓
Incident = RESOLVED
```

Phí mất thẻ là `50.000 VND`. Phí checkout/lưu xe là khoản riêng và không cộng vào payment mất thẻ.

## Trạng thái và quyền thao tác

| Incident | Frontend hiển thị | Thao tác được phép |
|---|---|---|
| `PENDING` | Đang chờ nhân viên xác minh | User chỉ xem/hủy nếu backend cho phép |
| `IN_PROGRESS` | Đang được xử lý | Hiển thị nút thanh toán cho chủ báo cáo; manager có thể tiếp nhận/xử lý |
| `WAITING_PAYMENT` | Chờ user thanh toán 50.000 VND | Chủ báo cáo được tạo/lấy URL VNPay; chưa được cấp thẻ mới |
| `IN_PROGRESS` sau khi payment `PAID` | Đã thanh toán, chờ cấp thẻ | Manager có thể cấp thẻ mới |
| `RESOLVED` | Đã hoàn tất | Chỉ xem, không cho thao tác lại |
| `CANCELLED` | Đã hủy | Chỉ xem |

Frontend phải lấy `status` và `paymentStatus` từ backend. Không suy ra đã thanh toán chỉ vì người dùng quay lại từ VNPay.

## API sử dụng

### 1. Lấy chi tiết báo cáo

```http
GET /api/incidents/{incidentId}
```

Dùng response để hiển thị `status`, thông tin thẻ cũ, session và payment hiện tại.

### 2. Tạo URL thanh toán phí mất thẻ

```http
POST /api/incidents/{incidentId}/lost-card-payment
```

API chỉ được gọi bởi user đang đăng nhập là chủ báo cáo. Staff không tạo VNPay thay user; staff dùng API ghi nhận tiền mặt ở phần dưới.

Không gửi số tiền từ frontend. Backend tự đặt số tiền là `50000`.

Response mẫu:

```json
{
  "incidentId": 101,
  "paymentId": 501,
  "amount": 50000,
  "paymentMethod": "VNPAY",
  "paymentStatus": "PENDING",
  "transactionRef": "TXN_LOST_...",
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?..."
}
```

Frontend chuyển hướng người dùng tới `paymentUrl`.

### 3. Xử lý sau khi VNPay redirect

VNPay redirect về backend tại:

```http
GET /api/payments/vnpay-return
```

Frontend không tự cập nhật incident. Sau khi người dùng quay lại trang kết quả, gọi lại:

```http
GET /api/incidents/{incidentId}
```

Chỉ khi response cho biết `paymentStatus = PAID` mới hiển thị “Thanh toán thành công”. Nếu `FAILED` hoặc chưa có `PAID`, cho phép thử thanh toán lại.

IPN của VNPay được backend xử lý tại:

```http
GET /api/payments/vnpay-ipn
```

Frontend không gọi IPN.

### 4. Manager cấp thẻ mới

Chỉ hiển thị thao tác này cho `MANAGER` hoặc `ADMIN`, sau khi:

- `incident.status` đang là `IN_PROGRESS` sau thanh toán.
- `paymentStatus = PAID`.
- Xe không còn session `ACTIVE`.
- Có thẻ thay thế ở trạng thái khả dụng.

Gọi:

```http
PUT /api/incidents/{incidentId}/replace-monthly-card
Content-Type: application/json
```

Body:

```json
{
  "replacementCardId": 9001
}
```

Frontend không cho chọn thẻ đã mất làm thẻ thay thế. Response trả về thông tin thẻ mới, báo cáo và `replacementAt`.

## Thanh toán tiền mặt tại quầy

Nếu user trả trực tiếp 50.000 VND cho staff, frontend dùng luồng tiền mặt thay vì VNPay.

### Staff ghi nhận tiền

```http
POST /api/incidents/{incidentId}/lost-card-payment/cash
```

```json
{
  "receiptNumber": "RC-20260808-001",
  "note": "Đã thu phí mất thẻ tại quầy"
}
```

Staff ghi nhận tiền khi report còn `IN_PROGRESS` hoặc `WAITING_PAYMENT`. Sau khi ghi nhận, payment được xác nhận ngay:

```text
Đã thanh toán tiền mặt, chờ manager cấp thẻ mới.
```

### Manager xử lý sau khi thanh toán tiền mặt

Manager không cần xác minh payment lần nữa. Sau khi tải lại report và thấy `paymentStatus = PAID`, manager chỉ gọi API cấp thẻ mới.

Frontend phải tự điền thông tin thanh toán từ response `GET /api/incidents/{incidentId}` hoặc response của API xác minh. Manager không nhập lại số tiền/biên lai nếu staff đã ghi nhận. Nếu manager là người trực tiếp thu tiền, manager được nhập số biên lai một lần qua API `/cash`; request xác minh vẫn không gửi `amount`.

Khi thanh toán thành công, màn hình manager hiển thị dạng chỉ đọc:

```text
Phí mất thẻ: 50.000 VND
Phương thức: CASH hoặc VNPAY
Trạng thái: PAID
Số biên lai/Mã giao dịch: ...
Người thu tiền: ...
Người xác nhận: ...
```

Frontend không hiển thị ô nhập số tiền cho manager. Nút **Cấp thẻ mới** chỉ mở khi `paymentStatus = PAID` và báo cáo là thẻ tháng.

### API xác nhận tiền mặt riêng

```http
POST /api/incidents/{incidentId}/lost-card-payment/cash/verify
```

Chỉ sau response có `paymentStatus = PAID`, frontend mới hiển thị nút **Cấp thẻ mới**. Frontend không tự gửi `paymentStatus = PAID` và không tự chuyển incident sang `RESOLVED`.

Payment tiền mặt cần hiển thị riêng:

- `paymentMethod = CASH`.
- Số biên lai.
- Người thu tiền.
- Người xác nhận.
- Thời gian thu/xác nhận.

Với xe đang trong bãi, phí checkout vẫn là một payment khác. Với xe chưa vào bãi, ẩn toàn bộ nút checkout và barrier.

## Quy tắc phối hợp VNPay và tiền mặt

Frontend hiển thị phương thức theo quyền, không cho staff tạo giao dịch VNPay thay user:

| Người dùng | Phương thức | API | Điều kiện |
|---|---|---|---|
| USER | VNPay | `POST /api/incidents/{id}/lost-card-payment` | `WAITING_PAYMENT`, chưa có payment |
| STAFF | Tiền mặt | `POST /api/incidents/{id}/lost-card-payment/cash` | Report `IN_PROGRESS`/`WAITING_PAYMENT`, chưa có payment |
| MANAGER | Cấp thẻ tháng mới | `PUT /api/incidents/{id}/replace-monthly-card` | `paymentStatus = PAID` |

Sau khi một phương thức được tạo, ẩn phương thức còn lại và gọi lại API lấy incident mới. Không tạo payment thứ hai khi user bấm nhiều lần hoặc khi staff đã ghi nhận tiền mặt.

### Trường hợp guest/thẻ lượt

Nếu `cardType` là `DAILY` hoặc báo cáo không gắn với vé tháng, frontend không hiển thị luồng thanh toán/cấp lại thẻ tháng. Manager chỉ cần xác minh thông tin báo mất theo API:

```http
PUT /api/incidents/{incidentId}/verify-lost-card
```

Sau khi xác minh, frontend tải lại report và hiển thị kết quả xác nhận. Không hiển thị nút **Cấp thẻ tháng mới**. Phí checkout hoặc xử lý xe ra bãi, nếu có, là luồng riêng.

### 5. Manager hoàn tất báo cáo

Sau khi cấp thẻ mới thành công, gọi:

```http
PUT /api/incidents/{incidentId}/resolve
Content-Type: application/json
```

Body mẫu:

```json
{
  "resolutionNotes": "Đã xác minh, user đã thanh toán phí mất thẻ và được cấp thẻ mới.",
  "lostCardFee": 50000
}
```

Backend sẽ từ chối nếu payment chưa `PAID`. Frontend chỉ hiển thị nút Resolve sau khi đã có payment thành công và thẻ thay thế.

## Hai trường hợp xe

### Xe vẫn còn trong bãi

- Sau khi báo mất, hiển thị cảnh báo không tự ý rời bãi.
- User thanh toán phí mất thẻ 50.000 VND.
- User phải hoàn tất checkout/phí gửi xe riêng theo quy trình bãi.
- Sau khi session không còn `ACTIVE`, manager cấp thẻ mới.
- Manager resolve báo cáo.

Frontend không tự gọi checkout, không tự mở barrier và không đánh dấu session hoàn tất.

### Xe không còn trong bãi

- User vẫn báo mất thẻ và thanh toán 50.000 VND.
- Manager cấp thẻ tháng thay thế sau khi payment `PAID`.
- Manager resolve báo cáo.
- Không hiển thị phí lưu xe hoặc thao tác barrier trong luồng này.

## Quy tắc UI bắt buộc

- Nút **Thanh toán VNPay** chỉ xuất hiện khi user là chủ báo cáo, incident là `WAITING_PAYMENT` và chưa có payment.
- Nút **Ghi nhận tiền mặt** chỉ xuất hiện cho staff khi incident là `IN_PROGRESS` hoặc `WAITING_PAYMENT` và chưa có payment.
- Không hiển thị nút **Xác nhận tiền mặt** cho manager khi payment đã `PAID`; chỉ hiển thị nút **Cấp thẻ mới**.
- Số tiền/biên lai trên màn hình manager luôn read-only và lấy từ backend; manager chỉ được cấp thẻ sau khi payment `PAID`.
- Với thẻ khách/thẻ lượt (`DAILY`), chỉ hiển thị thao tác xác minh; ẩn thanh toán phí mất thẻ và cấp thẻ tháng.
- Nút **Cấp thẻ mới** chỉ xuất hiện cho manager/admin sau khi payment `PAID` và session không còn `ACTIVE`.
- Nút **Hoàn tất** chỉ xuất hiện sau khi thẻ mới đã được cấp và payment `PAID`.
- Nếu user bấm thanh toán nhiều lần, gọi lại endpoint và dùng payment đang `PENDING` thay vì tạo giao dịch mới.
- Không tin `vnp_ResponseCode` từ URL frontend; trạng thái cuối cùng phải lấy từ API backend.
- Khi API trả lỗi `400/403/404`, hiển thị message backend và không tự chuyển sang bước tiếp theo.

## User hủy báo cáo khi chưa thanh toán

Chủ báo cáo được phép hủy báo cáo mất thẻ của chính mình khi payment chưa `PAID` và chưa được cấp thẻ thay thế.

```http
PUT /api/incidents/{incidentId}/cancel
Content-Type: application/json
```

Body:

```json
{
  "cancellationReason": "Báo nhầm, đã tìm lại được thẻ"
}
```

Frontend chỉ hiển thị nút **Hủy báo cáo** cho chủ báo cáo khi:

- `status` là `PENDING`, `IN_PROGRESS` hoặc `WAITING_PAYMENT`.
- `paymentStatus` chưa phải `PAID`.
- `replacementCardId` chưa có.

Khi hủy thành công, backend chuyển incident sang `CANCELLED`, hủy payment đang chờ nếu có và vô hiệu hóa URL VNPay cũ. Frontend phải đóng modal, cập nhật lại danh sách và không cho thanh toán/cấp thẻ tiếp tục cho incident đó.

## Thứ tự gọi API chuẩn

```text
GET incident
  → POST /lost-card-payment
  → redirect paymentUrl
  → GET incident, chờ paymentStatus = PAID
  → manager PUT /replace-monthly-card
  → manager PUT /resolve
```
