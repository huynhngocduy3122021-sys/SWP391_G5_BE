# Luồng backend thống nhất: user mất thẻ tháng

## Nguyên tắc

Chỉ dùng một luồng cho mất thẻ tháng:

```text
USER tạo report bằng tài khoản của chính mình
        ↓
PENDING
        ↓ Manager xác minh
IN_PROGRESS
        ↓
Manager cấp thẻ tháng mới
        ↓
RESOLVED
```

Không tạo report thay cho user bằng tài khoản staff, không tạo report thứ hai cho cùng thẻ và không tách thành các luồng VNPay/tiền mặt khác nhau trong nghiệp vụ cấp lại thẻ.

## Tạo report

```http
POST /api/incidents/lost-card
```

Chỉ cho phép `USER` gọi API. Backend lấy `reporter` từ access token, không nhận `reporterId` từ body.

Backend kiểm tra:

- Thẻ có loại `MONTHLY`.
- Thẻ và phương tiện thuộc user đang đăng nhập.
- Chưa có report mất thẻ tháng đang mở cho thẻ này.
- Tự xác định `parkingSession` nếu xe đang trong bãi; không có session vẫn tạo report.
- Khóa thẻ cũ ở trạng thái `LOST`.

Report mới có:

```text
status = PENDING
cardType = MONTHLY
reporter = USER hiện tại
parkingSessionId = nullable
```

## Manager xác minh và cấp thẻ

Manager gọi:

```http
PUT /api/incidents/{id}/verify-lost-card
```

Sau khi xác minh đúng user, thẻ và biển số:

```text
PENDING → IN_PROGRESS
```

Manager chọn thẻ tháng khả dụng và gọi:

```http
PUT /api/incidents/{id}/replace-monthly-card
```

```json
{
  "replacementCardId": 9001
}
```

Backend phải kiểm tra trong một transaction:

- Report thuộc thẻ tháng.
- Report đã được xác minh (`IN_PROGRESS`).
- Thẻ cũ đang `LOST`.
- Thẻ mới cùng chi nhánh, cùng loại `MONTHLY` và đang `AVAILABLE`.
- Thẻ mới chưa có vé tháng đang hoạt động.
- Nếu session còn `ACTIVE`, sau khi payment `PAID` backend chuyển session sang thẻ mới khi replacement.

Sau khi cấp thành công, chuyển vé tháng sang thẻ mới, lưu người cấp/thời gian cấp và giữ report ở `IN_PROGRESS` cho đến khi hoàn tất nghiệp vụ.

Cuối cùng manager gọi:

```http
PUT /api/incidents/{id}/resolve
```

Backend chuyển `IN_PROGRESS → RESOLVED` sau khi đã có `replacementCardId`.

## Trường hợp xe đang trong bãi và user muốn checkout

Mất thẻ tháng không được dùng làm lý do tự động mở barrier hoặc tự động kết thúc session.

Luồng checkout:

1. Staff tìm report mở theo user + thẻ + biển số.
2. Đối chiếu user, giấy tờ/nhận dạng, biển số và session đang `ACTIVE`.
3. Dùng quy trình checkout mất thẻ hiện có, liên kết đúng `parkingSessionId` với report.
4. Tính và thu phí gửi xe/checkout riêng; không gộp nhầm vào dữ liệu cấp lại thẻ tháng.
5. Chỉ khi checkout thành công và session chuyển `COMPLETED` mới cho phép manager cấp thẻ mới.
6. Không xóa report sau checkout; report là căn cứ để cấp thẻ và audit.

Nếu xác minh không thành công, giữ session `ACTIVE`, không mở barrier và không cấp thẻ mới.

## Trường hợp xe không trong bãi

`parkingSessionId = null` là hợp lệ. Manager xác minh ownership rồi cấp thẻ mới, không chạy checkout và không thao tác barrier.

## Hủy report

User được hủy report của mình khi report chưa cấp thẻ mới và chưa `RESOLVED`:

```http
PUT /api/incidents/{id}/cancel
```

Khi hủy, backend chuyển `PENDING/IN_PROGRESS → CANCELLED`, mở khóa thẻ cũ theo chính sách và ghi audit log. Sau khi manager đã cấp thẻ mới thì không cho hủy.

## Loại bỏ luồng gây xung đột

Trong luồng thống nhất này, frontend/backend không tạo payment riêng để quyết định cấp thẻ. Nếu chính sách bãi vẫn thu phí mất thẻ, ghi nhận phí trong nghiệp vụ cấp thẻ bằng một field/audit riêng; không tạo thêm report hoặc payment order làm điều kiện rẽ nhánh.

## Kiểm thử bắt buộc

- User tạo report bằng tài khoản của chính mình: thành công, `201`, reporter đúng user.
- Staff không thể tạo report thay user.
- Monthly user có xe trong bãi: report có session `ACTIVE`, không kết thúc session khi tạo report.
- Monthly user không có xe trong bãi: report tạo được với session null.
- Checkout chỉ thành công sau khi staff xác minh report và thu phí checkout.
- Không yêu cầu checkout thẻ cũ trước khi cấp thẻ mới; session `ACTIVE` sẽ được chuyển sang thẻ mới.
- Manager cấp thẻ mới thành công sau checkout hoặc ngay nếu session null.
- User không thể hủy sau khi đã cấp thẻ mới.
# Cập nhật luồng thanh toán VNPay

Luồng hiện hành đã khôi phục thanh toán phí mất thẻ, nhưng chỉ còn một phương thức là VNPay và không cần manager xác minh trước.

1. User tạo report mất thẻ tháng, report ở `PENDING`.
2. Nếu `parkingSessionId` còn `ACTIVE`, user dùng thẻ guest/regular tại quầy và gọi:

```http
POST /api/incidents/{id}/lost-card/guest-checkout
```

Body bắt buộc có `cardCode` của thẻ guest và `licensePlate`. Backend kiểm tra thẻ guest cùng chi nhánh, checkout phiên cũ và tạo một payment VNPay gồm phí checkout (nếu có) + `50.000 VND` phí mất thẻ.

3. Nếu xe không ở bãi (`parkingSessionId = null` hoặc session đã hoàn tất), user gọi:

```http
POST /api/incidents/{id}/lost-card-payment
```

Backend tạo payment VNPay `50.000 VND` và chuyển report sang `WAITING_PAYMENT`.

4. VNPay callback/IPN thành công chuyển payment sang `PAID`, session (nếu có) sang `COMPLETED` và report sang `IN_PROGRESS`.
5. Chỉ sau `PAID + IN_PROGRESS`, manager được gọi `PUT /api/incidents/{id}/replace-monthly-card`. Sau khi cấp thẻ mới, gọi resolve. Trong thời gian chờ xử lý, user sử dụng thẻ guest; sau replacement thì dùng thẻ tháng mới.

Các API thanh toán tiền mặt và xác minh manager không thuộc luồng mới.

## Báo mất thẻ guest do staff thực hiện

`POST /api/incidents/lost-card` cho phép `USER` và `STAFF`. `STAFF` chỉ được tạo báo cáo cho thẻ guest/regular gắn với phiên gửi xe đang `ACTIVE`; nếu thẻ là `MONTHLY`, backend từ chối và yêu cầu chính chủ `USER` tự báo cáo.

## Cập nhật khi xe vẫn còn trong bãi

Sau khi payment VNPay đã `PAID`, manager được cấp thẻ mới ngay cả khi session cũ vẫn `ACTIVE`. Backend chuyển `parkingSession.parkingCard` từ thẻ cũ sang thẻ mới và đặt thẻ mới `IN_USE`. User dùng mã thẻ mới để checkout; không được yêu cầu checkout bằng thẻ cũ trước khi cấp replacement.
