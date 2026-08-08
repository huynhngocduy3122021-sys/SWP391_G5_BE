# Luồng frontend thống nhất: user mất thẻ tháng

## Luồng duy nhất

```text
User báo mất thẻ
        ↓
Report PENDING
        ↓ Manager xác minh
Report IN_PROGRESS
        ↓
Xe trong bãi? ── Có ──> Checkout mất thẻ
        │                    ↓
        └─ Không             Manager cấp thẻ mới
                             ↓
                         Resolve report
```

Frontend chỉ cho user tạo report của chính mình. Staff không có màn hình tạo report thay user.

## User tạo report

```http
POST /api/incidents/lost-card
```

Sau khi thành công, hiển thị:

```text
Đã gửi báo cáo mất thẻ tháng, đang chờ manager xác minh.
```

Frontend không hiển thị nút VNPay/tiền mặt trong luồng thống nhất này và không tự chuyển report sang `WAITING_PAYMENT`.

## Manager xác minh

Manager xem report và bấm **Xác minh**:

```http
PUT /api/incidents/{id}/verify-lost-card
```

Sau response:

```json
{
  "cardType": "MONTHLY",
  "status": "IN_PROGRESS",
  "parkingSessionId": 123
}
```

Frontend hiển thị thông tin thẻ cũ, user, biển số và nút xử lý tiếp theo. Không hiển thị form nhập số tiền thanh toán để quyết định cấp thẻ.

## User muốn checkout khi xe đang trong bãi

Khi `parkingSessionStatus = ACTIVE`:

- Hiển thị cảnh báo “Xe cần được xử lý checkout mất thẻ tại quầy”.
- Không tự gọi checkout khi user mở report.
- Không tự mở barrier.
- Staff đối chiếu user/giấy tờ/biển số tại quầy.
- Staff thực hiện checkout và thu phí gửi xe riêng.
- Frontend cập nhật lại report sau khi session chuyển `COMPLETED`.

Chỉ hiển thị nút **Cấp thẻ mới** khi:

```javascript
status === "IN_PROGRESS" &&
paymentStatus === "PAID"
```

## Xe không trong bãi

Khi `parkingSessionId = null` hoặc session không `ACTIVE`:

- Ẩn toàn bộ nút checkout/barrier.
- Manager có thể cấp thẻ tháng mới sau khi xác minh.

## Manager cấp thẻ mới

Gọi:

```http
PUT /api/incidents/{id}/replace-monthly-card
```

```json
{
  "replacementCardId": 9001
}
```

Sau đó gọi:

```http
PUT /api/incidents/{id}/resolve
```

Frontend không tự đánh dấu `RESOLVED`; phải lấy response backend rồi refresh danh sách.

## User hủy report

Chỉ hiển thị **Hủy báo cáo** khi:

```javascript
(status === "PENDING" || status === "IN_PROGRESS") &&
replacementCardId == null
```

Gọi:

```http
PUT /api/incidents/{id}/cancel
```

Sau khi hủy, đóng modal và không cho checkout/cấp thẻ tiếp tục.

## Quy tắc UI bắt buộc

- Không có bước thanh toán riêng trong luồng cấp lại thẻ tháng thống nhất.
- Không có nút staff tạo report thay user.
- Vẫn cho cấp thẻ mới khi session còn `ACTIVE` nếu payment đã `PAID`; backend sẽ chuyển session sang thẻ mới.
- Không cho user tự chọn session hoặc tự đánh dấu xe đã ra bãi.
- Luôn gọi lại `GET /api/incidents/{id}` sau verify, checkout, replacement và resolve.
- Dùng `cardType`, `status`, `parkingSessionId`, `parkingSessionStatus`, `replacementCardId` từ backend làm nguồn dữ liệu cuối cùng.
# Cập nhật luồng thanh toán VNPay

Frontend không gọi `verify-lost-card` trước khi thanh toán.

Staff tạo báo cáo mất thẻ guest bằng cùng endpoint `/api/incidents/lost-card`; token staff phải có role `STAFF`. Không dùng endpoint này để staff tạo báo cáo mất thẻ tháng.

- Xe còn trong bãi: yêu cầu user nhập/quét mã thẻ guest và biển số, sau đó gọi `POST /api/incidents/{id}/lost-card/guest-checkout`; dùng `paymentUrl` trả về để chuyển sang VNPay.
- Xe không còn trong bãi: gọi `POST /api/incidents/{id}/lost-card-payment`; dùng `paymentUrl` trả về để chuyển sang VNPay.
- Sau redirect, đọc `paymentType = LOST_CARD`, `paymentStatus = PAID`, `success = true`, rồi gọi lại `GET /api/incidents/{id}`.
- Chỉ hiển thị **Cấp thẻ mới** khi report là `IN_PROGRESS`, payment là `PAID`, và `replacementCardId` chưa có.
- Trong lúc report chờ thanh toán, user dùng thẻ guest; sau khi manager cấp replacement thì dùng thẻ mới.
- Không hiển thị form tiền mặt, nút xác minh tiền mặt hoặc nút cấp thẻ khi payment chưa `PAID`.

Nếu xe vẫn đang trong bãi, sau khi payment thành công frontend vẫn hiển thị **Cấp thẻ mới**. Manager cấp xong thì frontend dùng `replacementCardCode` làm mã checkout; không bắt user checkout bằng mã thẻ cũ.

Request cấp thẻ phải gửi đúng ID số của thẻ mới, không gửi mã thẻ:

```js
await api.put(`/api/incidents/${incidentId}/replace-monthly-card`, {
  replacementCardId: Number(selectedCard.parkingCardId)
});
```

Frontend không được lấy `selectedCard.id`, `cardCode` hoặc truyền `undefined`. Nếu backend trả lỗi, ưu tiên hiển thị `message` trong response thay vì hiển thị nguyên JSON lỗi 500.
