# Báo mất thẻ xe — Backend

> **Luồng chuẩn mới:** xem [unified-lost-monthly-card-flow.md](./unified-lost-monthly-card-flow.md). Các phần payment VNPay/tiền mặt cũ trong file này không còn là luồng chính để cấp lại thẻ tháng.

## Vấn đề cần sửa

## Phân biệt thẻ tháng và phiên gửi xe

`monthlyPass` là quyền sử dụng dài hạn; `parkingSession` chỉ phản ánh xe đang ở trong bãi. Vì vậy `parkingSessionId` phải nullable và không được dùng làm điều kiện tạo báo cáo mất thẻ tháng.

### Tình huống 1: mất thẻ tháng, xe vẫn trong bãi

1. Tạo báo cáo với `cardType = MONTHLY`, liên kết phiên đang hoạt động nếu tìm thấy.
2. Khóa thẻ tháng cũ ở trạng thái `LOST_PENDING_VERIFICATION` để không bị dùng lại; không kết thúc phiên và không tự động mở barrier.
3. Đưa yêu cầu vào hàng chờ xác minh. Nhân viên đối soát tài khoản, biển số, ảnh/nhận dạng theo chính sách và trạng thái phiên.
4. Nếu hợp lệ, cho phép xử lý xe ra bằng quy trình mất thẻ riêng, tính phí theo chính sách (nếu có), sau đó kết thúc phiên và chuyển báo cáo thành `RESOLVED`.
5. Nếu không hợp lệ, giữ phiên an toàn, chuyển `REJECTED` và ghi lý do/audit log.

### Tình huống 2: mất thẻ tháng, xe không trong bãi

1. Tạo báo cáo dù không có phiên (`parkingSessionId = null`); không tạo phí lưu xe và không có thao tác barrier.
2. Kiểm tra quyền sở hữu thẻ tháng và phương tiện từ tài khoản, không chỉ dựa vào biển số nhập tay.
3. Khóa thẻ cũ sau khi xác minh tối thiểu; chuyển quyền sang thẻ thay thế hoặc tạo yêu cầu cấp thẻ mới.
4. Khi người dùng/xe vào bãi sau đó, tìm báo cáo mở theo tài khoản + bãi + biển số và yêu cầu xác minh tại cổng nếu cần.

Không cho client tự chọn “xe trong bãi/ngoài bãi” làm kết quả cuối cùng; backend phải suy ra từ phiên đang hoạt động và trạng thái cổng.

Báo mất thẻ hiện bị gắn với điều kiện xe đã vào bãi. Điều này loại bỏ trường hợp người dùng làm mất thẻ trước khi vào cổng. Cần tách **báo cáo mất thẻ** khỏi **phiên gửi xe**: phiên gửi xe là liên kết tùy chọn, được gắn sau nếu có hoặc được tạo khi xe vào bãi.

## Mô hình dữ liệu đề xuất

```text
LostCardReport
- id
- reporterUserId                 NOT NULL
- parkingLotId                   NOT NULL
- cardType                       NOT NULL: MONTHLY | DAILY
- vehicleId                      NULL
- licensePlate                   NOT NULL
- parkingSessionId               NULL
- lostStage                      NOT NULL: BEFORE_ENTRY | INSIDE_PARKING | UNKNOWN
- lostAt                         NULL
- description                    NULL
- status                         NOT NULL: SUBMITTED ... RESOLVED
- verificationMethod             NOT NULL
- verifiedBy                     NULL
- verifiedAt                     NULL
- resolution                     NULL
- createdAt, updatedAt
```

Không đặt foreign key `parkingSessionId` là bắt buộc. Nếu hệ thống không muốn cho phép xoá phiên gửi xe, giữ liên kết nullable và không cascade xoá báo cáo.

## API contract

### Tạo báo cáo

`POST /api/lost-card-reports`

```json
{
  "parkingLotId": "lot-01",
  "cardType": "MONTHLY",
  "vehicleId": "vehicle-01",
  "licensePlate": "51A-123.45",
  "lostStage": "BEFORE_ENTRY",
  "lostAt": "2026-08-08T08:00:00+07:00",
  "description": "Mất thẻ trước cổng"
}
```

Backend lấy `reporterUserId` từ access token, không nhận từ body. `parkingSessionId` có thể nhận từ request đã xác minh nhưng không được dùng làm điều kiện bắt buộc. Sau khi tạo, trả `201` cùng `id`, `status = SUBMITTED`, `parkingSessionId` (có thể `null`) và hướng dẫn tiếp theo.

### Xem và xử lý

- `GET /api/lost-card-reports/{id}`: chỉ chủ báo cáo và nhân viên có quyền được xem.
- `GET /api/lost-card-reports?mine=true`: danh sách báo cáo của người dùng.
- `PATCH /api/lost-card-reports/{id}/verify`: nhân viên xác minh danh tính, biển số và trạng thái thẻ.
- `PATCH /api/lost-card-reports/{id}/resolve`: ghi nhận kết quả, phí (nếu có), thẻ thay thế hoặc lý do từ chối.
- `POST /api/lost-card-reports/{id}/cancel`: chỉ cho phép khi chưa xử lý.

## Quy trình nghiệp vụ

1. Xác thực người dùng và kiểm tra bãi xe, biển số hợp lệ.
2. Tìm báo cáo đang mở theo người dùng + bãi + biển số; nếu có thì trả báo cáo cũ để tránh trùng.
3. Tìm phiên gửi xe đang hoạt động (nếu có). Có thì gắn `parkingSessionId`; không có thì để `null`, vẫn tạo báo cáo.
4. Chuyển trạng thái `SUBMITTED` → `PENDING_VERIFICATION` và ghi audit log.
5. Khi xe đến cổng, hệ thống dò báo cáo mở theo bãi/biển số/người dùng; hiển thị cảnh báo cho nhân viên hoặc đưa vào hàng chờ xác minh. Không tự động cho xe ra/vào chỉ vì có báo cáo.
6. Nhân viên xác minh bằng tài khoản, giấy tờ, biển số, thời gian và chính sách của bãi. Sau xác minh, cập nhật `APPROVED` hoặc `REJECTED`.
7. Khi xử lý xong, ghi `resolution`, phí nếu có, người xử lý và thời gian; chuyển `RESOLVED`.

## Quy tắc an toàn và nhất quán

- Dùng transaction khi tạo báo cáo và liên kết phiên gửi xe.
- Dùng `Idempotency-Key`; thêm unique rule cho một báo cáo đang mở của cùng người dùng, bãi và biển số.
- Không cho người dùng tự đổi `status`, `verifiedBy`, `fee` hoặc `resolution`.
- Không đánh dấu thẻ mất hoặc khóa thẻ vật lý trước khi có kiểm tra quyền; nếu cần khóa tạm thời, phải ghi audit log và có thao tác mở khóa.
- Khi một phiên gửi xe mới được tạo, job/event có thể liên kết báo cáo `BEFORE_ENTRY` phù hợp; liên kết này phải kiểm tra quyền sở hữu và biển số, không ghép mơ hồ.
- Mọi chuyển trạng thái phải kiểm tra state machine và lưu lịch sử.

## Thanh toán phí mất thẻ bằng VNPay

Phí mất thẻ tháng là **50.000 VND**, đặt trong cấu hình backend (`LOST_MONTHLY_CARD_FEE`), không nhận số tiền từ frontend. Phí phạt và phí gửi xe/checkout là hai khoản riêng.

### Luồng xử lý

1. Khi nhân viên xác minh hợp lệ, chuyển báo cáo sang `APPROVED` và tạo một `PaymentOrder` với `amount = 50000`, `currency = VND`, `incidentId`, `userId`, `status = UNPAID`.
2. Chỉ cho phép một khoản phải thu đang mở cho mỗi báo cáo. Không tạo giao dịch mới khi user bấm lại; dùng `Idempotency-Key`.
3. API VNPay chỉ được gọi bởi chủ báo cáo khi incident là `WAITING_PAYMENT`. Staff không tạo VNPay thay user; staff dùng API tiền mặt ở phần dưới. Backend sinh `vnp_TxnRef`, số tiền và chữ ký VNPay, lưu order trước khi trả `paymentUrl`.
4. `POST /api/payments/vnpay-ipn` nhận callback server-to-server từ VNPay. Backend phải xác thực chữ ký, mã giao dịch, số tiền và trạng thái đơn hàng. Chỉ IPN hợp lệ với `vnp_ResponseCode = 00` và `vnp_TransactionStatus = 00` mới chuyển order sang `PAID`.
5. `GET /api/payments/{transactionId}` cung cấp trạng thái cho frontend. Redirect về frontend chỉ dùng để hiển thị tạm thời, không dùng làm bằng chứng thanh toán.
6. Khi order `PAID`, ghi mã giao dịch/biên nhận và chuyển báo cáo sang `PAYMENT_COMPLETED` hoặc `READY_FOR_RESOLUTION`.

### Sau khi thanh toán

- Xe đang trong bãi: giữ session an toàn; nhân viên tiếp tục đối soát và checkout. Không tự động mở barrier chỉ vì IPN thành công. Phí giữ xe được tính riêng.
- Xe ngoài bãi: cho phép cấp thẻ tháng thay thế; sau khi cấp thành công mới chuyển báo cáo sang `RESOLVED`.
- Thanh toán thất bại/hết hạn: báo cáo vẫn ở `APPROVED`, order ở `FAILED`/`EXPIRED`, cho phép tạo order mới.
- Báo cáo bị từ chối trước khi thanh toán: hủy khoản phải thu. Nếu đã thanh toán, phải dùng quy trình hoàn tiền riêng.

Nên có các trạng thái payment `UNPAID`, `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `REFUNDED`. Chỉ cho phép `RESOLVED` khi `paymentStatus = PAID`, trừ trường hợp nhân viên ghi nhận chính sách miễn phí.

## Thanh toán tiền mặt tại quầy

VNPay không phù hợp khi staff đã trực tiếp thu 50.000 VND từ user. Không cập nhật `paymentStatus` trực tiếp từ frontend hoặc sửa DB. Bổ sung luồng thu tiền mặt có audit:

```text
Staff ghi nhận đã thu tiền
        ↓
Manager xác nhận khoản thu
        ↓
Payment = PAID, method = CASH
        ↓
Manager cấp thẻ mới
        ↓
Resolve incident
```

### API đề xuất

```http
POST /api/incidents/{id}/lost-card-payment/cash
```

Staff có thể gửi ngay sau khi tạo report, kể cả khi report còn `PENDING` và manager chưa xác minh:

```json
{
  "receiptNumber": "RC-20260808-001",
  "note": "Đã thu phí mất thẻ tại quầy"
}
```

Backend phải tự cố định `amount = 50000`, kiểm tra incident là `LOST_CARD`, trạng thái còn mở (`PENDING`, `IN_PROGRESS` hoặc `WAITING_PAYMENT`), chưa `PAID`, chưa cấp thẻ thay thế và đúng chi nhánh. Bản ghi nên có thêm `cashCollectedBy`, `cashCollectedAt`, `cashVerifiedBy`, `cashVerifiedAt`, `receiptNumber` và trạng thái `CASH_PENDING_VERIFICATION`.

API xác nhận riêng (chỉ dùng khi frontend tách riêng thao tác xác nhận payment):

```http
POST /api/incidents/{id}/lost-card-payment/cash/verify
```

Backend cập nhật trong transaction:

```java
payment.setAmount(new BigDecimal("50000"));
payment.setPaymentMethod(PaymentMethod.CASH);
payment.setPaymentStatus(PaymentStatus.PAID);
payment.setPaidAt(LocalDateTime.now());
incident.setStatus(IncidentStatus.IN_PROGRESS);
```

Khi manager gọi `PUT /api/incidents/{id}/verify-lost-card`, backend cũng phải kiểm tra payment tiền mặt. Nếu payment là `CASH_PENDING_VERIFICATION`, thao tác xác minh report đồng thời chuyển payment thành `PAID`, lưu manager xác nhận và chuyển incident thẳng sang `IN_PROGRESS`; không được đặt lại `WAITING_PAYMENT`.

Frontend không được yêu cầu manager nhập lại số tiền. Backend là nguồn dữ liệu duy nhất cho `amount`, `paymentMethod`, `paymentStatus`, biên lai và mã giao dịch. Sau khi VNPay IPN hợp lệ hoặc manager xác nhận tiền mặt, response report phải trả lại đầy đủ payment để frontend hiển thị dạng chỉ đọc.

## Trường hợp guest/thẻ lượt

`DAILY`/guest không sử dụng quy trình thay thế thẻ tháng. Khi manager xác minh đúng thông tin:

1. Không tạo payment phí mất thẻ tháng 50.000 VND.
2. Không yêu cầu `replacementCardId` và không gọi API cấp thẻ tháng.
3. Trả kết quả xác minh cho frontend; phí checkout hoặc xử lý xe ra bãi, nếu có, xử lý riêng.

Backend phải phân nhánh theo `cardType` trước khi tạo phí. Không được áp dụng logic `WAITING_PAYMENT` của thẻ tháng cho thẻ guest/thẻ lượt.

## Quy tắc dùng chung cho tiền mặt và VNPay

Hai phương thức đều được hỗ trợ nhưng một báo cáo mất thẻ chỉ được có một khoản thanh toán đang mở và chỉ được thanh toán thành công một lần.

| Phương thức | Người gọi | Điều kiện | Xác nhận cuối |
|---|---|---|---|
| VNPay | Chủ báo cáo (`USER`) | Incident `WAITING_PAYMENT`, chưa có payment khác | VNPay IPN hợp lệ |
| Tiền mặt | Staff/Manager/Admin ghi nhận | Incident còn mở (`PENDING`, `IN_PROGRESS` hoặc `WAITING_PAYMENT`), chưa có payment khác | Manager xác nhận |

Backend phải khóa incident/payment trong transaction. Nếu đã có payment `PENDING`, `CASH_PENDING_VERIFICATION` hoặc `PAID`, không tạo payment thứ hai; trả lỗi `PAYMENT_METHOD_ALREADY_SELECTED`.

Frontend không được truyền `amount`, `paymentStatus`, `paymentMethod`, `paidAt` hoặc `incident.status` để cập nhật trực tiếp. Backend tự cố định phí 50.000 VND và xử lý state machine:

```text
WAITING_PAYMENT
   ├─ USER tạo VNPay → PENDING → VNPay IPN thành công → PAID
   └─ STAFF ghi nhận CASH → CASH_PENDING_VERIFICATION
                              → MANAGER xác nhận → PAID
PAID → MANAGER cấp thẻ mới → RESOLVED
```

Khi một phương thức đã được tạo, phương thức còn lại phải bị từ chối. Nếu user hủy trước `PAID`, backend hủy payment chờ và vô hiệu hóa giao dịch VNPay cũ. Nếu đã `PAID`, phải dùng quy trình hoàn tiền riêng.

Trường hợp xe đang trong bãi vẫn phải checkout và thanh toán phí gửi xe riêng trước khi cấp thẻ. Trường hợp xe chưa vào bãi không được chạy checkout hoặc thao tác barrier.

## State machine tối thiểu

```text
SUBMITTED -> PENDING_VERIFICATION -> APPROVED -> RESOLVED
                               \-> REJECTED
SUBMITTED/PENDING_VERIFICATION -> CANCELLED
```

`parkingSessionId = null` hợp lệ ở `SUBMITTED` và `PENDING_VERIFICATION`. Đây là điểm chính để hỗ trợ báo mất thẻ trước khi xe vào bãi.

## Kiểm thử bắt buộc

- Tạo báo cáo `BEFORE_ENTRY` không có phiên gửi xe: thành công, `201`, session null.
- Mất thẻ tháng khi xe đang trong bãi: khóa thẻ cũ chờ xác minh, không kết thúc phiên và không mở barrier tự động.
- Mất thẻ tháng khi xe ngoài bãi: tạo báo cáo session null, không phát sinh phí lưu xe, hỗ trợ cấp thẻ thay thế.
- Tạo báo cáo khi đã có phiên: liên kết đúng phiên.
- Xe vào sau khi đã báo mất: tìm thấy báo cáo mở và đưa vào quy trình xác minh.
- Gửi lại cùng `Idempotency-Key`: không tạo bản ghi thứ hai.
- Người dùng không xem/sửa được báo cáo của người khác.
- Không cho cập nhật ngược trạng thái hoặc tự thay đổi phí/kết quả.
