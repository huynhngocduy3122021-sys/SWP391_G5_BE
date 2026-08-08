# Điều chỉnh backend: phân nhánh trạng thái báo mất thẻ

## Mục tiêu

Tách rõ hai nghiệp vụ ngay khi user tạo báo cáo:

1. Thẻ tháng (`MONTHLY`) của user: bỏ qua `PENDING`/`WAITING_VERIFICATION`, chuyển thẳng sang `WAITING_PAYMENT`.
2. Thẻ khách/thẻ lượt (`REGULAR`) của guest: chỉ tạo report gửi manager duyệt hoặc hủy; không tạo payment phí mất thẻ tháng và không yêu cầu cấp thẻ thay thế.

## Luồng trạng thái chuẩn

```text
MONTHLY + USER
POST /api/incidents/lost-card
        ↓
WAITING_PAYMENT, lostCardFee = 50000
        ↓
VNPay hoặc staff ghi nhận CASH
        ↓
PAID / IN_PROGRESS
        ↓
Manager cấp thẻ tháng mới
        ↓
RESOLVED

REGULAR/GUEST
POST /api/incidents/lost-card
        ↓
PENDING
        ↓ manager verify
IN_PROGRESS (đã duyệt, không tạo payment)
        ├─ manager resolve sau khi xử lý nghiệp vụ
        └─ CANCELLED nếu hủy
```

`WAITING_PAYMENT` chỉ được dùng cho thẻ tháng. Không áp dụng phí 50.000 VND của thẻ tháng cho thẻ `REGULAR`.

## Quy tắc khi tạo report

Backend lấy user từ access token, lấy loại thẻ từ `ParkingCard.type` và không tin `cardType` do frontend gửi để quyết định phí.

```java
boolean monthly = card.getType() == ParkingCardType.MONTHLY;

report.setStatus(monthly && reporter.getUserRole() == UserRole.USER
        ? IncidentStatus.WAITING_PAYMENT
        : IncidentStatus.PENDING);

report.setLostCardFee(monthly && reporter.getUserRole() == UserRole.USER
        ? new BigDecimal("50000")
        : BigDecimal.ZERO);
```

Khi là `MONTHLY + USER`:

- Khóa thẻ cũ.
- Tạo report ở `WAITING_PAYMENT`.
- User được gọi API tạo VNPay ngay.
- Staff/Manager/Admin có thể ghi nhận tiền mặt, payment chuyển thẳng `PAID`.
- Manager không cần duyệt report/payment lần nữa; chỉ tiếp tục cấp thẻ mới.

Khi là `REGULAR/GUEST`:

- Tạo report ở `PENDING`.
- `lostCardFee = 0`.
- Không tạo payment liên quan đến phí thay thẻ tháng.
- Không cho gọi API VNPay/cash payment của lost monthly card.
- Không yêu cầu `replacementCardId`.

## API manager

### Duyệt report

```http
PUT /api/incidents/{id}/verify-lost-card
```

Với `REGULAR/GUEST`, API chỉ xác minh thông tin và chuyển:

```text
PENDING → IN_PROGRESS
```

Với `MONTHLY` đã ở `WAITING_PAYMENT`, API không được bắt user xác minh lại. Nếu payment tiền mặt đã được staff/manager ghi nhận, payment đã là `PAID` và report chuyển sang `IN_PROGRESS`; manager chỉ cấp thẻ mới.

### Hủy report

```http
PUT /api/incidents/{id}/cancel
```

Manager có thể hủy report `PENDING` của guest hoặc report chưa thanh toán. Khi hủy:

- Chuyển report thành `CANCELLED`.
- Hủy payment đang chờ nếu có.
- Ghi audit log lý do và người hủy.
- Không cho gọi lại payment/cấp thẻ trên report đã hủy.

## Kiểm tra bắt buộc

- Monthly user tạo report: status ngay là `WAITING_PAYMENT`, fee 50.000.
- Regular guest tạo report: status là `PENDING`, fee 0, không có payment.
- Verify regular guest: không yêu cầu payment, không yêu cầu thẻ thay thế.
- Verify monthly cash đã thu: payment thành `PAID`, report thành `IN_PROGRESS`.
- Không cho `REGULAR` gọi API `/lost-card-payment` hoặc `/lost-card-payment/cash`.
