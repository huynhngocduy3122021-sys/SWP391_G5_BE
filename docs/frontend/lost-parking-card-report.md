# Báo mất thẻ xe — Frontend

## Mục tiêu

Cho phép người dùng tự báo mất thẻ xe ngay cả khi xe **chưa vào bãi**. Luồng không được phụ thuộc vào `parkingSession`, `entryTime` hoặc điều kiện “xe đã vào bãi”. Nếu xe đã vào bãi, báo mất thẻ vẫn dùng cùng một biểu mẫu và bổ sung thông tin phiên gửi xe nếu backend tìm thấy.

Phải phân biệt `monthlyPass` (thẻ tháng/quyền sử dụng dài hạn) với `parkingSession` (lượt xe đang ở trong bãi). Mất thẻ tháng không đồng nghĩa với mất lượt gửi xe.

## Hai tình huống thẻ tháng

| Tình huống | Cách frontend hiển thị và xử lý |
|---|---|
| Xe vẫn trong bãi | Cho phép báo mất thẻ; hiển thị cảnh báo không tự ý rời bãi. Sau khi gửi, yêu cầu ở trạng thái chờ xác minh và nhân viên đối soát xe/biển số trước khi cho xe ra. Không khóa hoặc kết thúc phiên chỉ ở frontend. |
| Xe không trong bãi | Vẫn cho phép báo mất thẻ, không yêu cầu `parkingSessionId`. Hiển thị hướng dẫn khóa thẻ cũ và cấp/thêm thẻ thay thế; không tạo phí lưu xe hoặc thao tác barrier. |

Form nên có `cardType = MONTHLY`; trạng thái xe hiện tại phải do backend xác định, không để người dùng tự quyết định kết quả xác minh.

## Luồng người dùng

1. Người dùng mở **Báo mất thẻ xe** từ trang tài khoản, danh sách xe hoặc màn hình cổng vào.
2. Chọn bãi xe, nhập/chọn biển số xe và thông tin xác minh theo chính sách hệ thống.
3. Chọn thời điểm mất thẻ (trước khi vào bãi / trong bãi / không xác định), nhập mô tả nếu cần và gửi yêu cầu.
4. Frontend gọi API tạo yêu cầu, hiển thị mã yêu cầu và trạng thái xử lý.
5. Người dùng có thể xem chi tiết, bổ sung thông tin hoặc hủy yêu cầu khi trạng thái còn cho phép.

## Dữ liệu biểu mẫu

```ts
type LostCardReportForm = {
  parkingLotId: string;
  cardType: 'MONTHLY' | 'DAILY';
  vehicleId?: string;
  licensePlate: string;
  lostAt?: string;
  lostStage: 'BEFORE_ENTRY' | 'INSIDE_PARKING' | 'UNKNOWN';
  description?: string;
};
```

`vehicleId` và phiên gửi xe là dữ liệu bổ sung, không phải điều kiện bắt buộc để tạo báo cáo. Với `BEFORE_ENTRY`, giao diện phải cho phép gửi khi không có `parkingSessionId`.

## API và trạng thái giao diện

- `POST /api/lost-card-reports`: tạo báo cáo; gửi `parkingSessionId` chỉ khi đã biết.
- `GET /api/lost-card-reports/{id}`: xem trạng thái và hướng dẫn tiếp theo.
- `POST /api/lost-card-reports/{id}/cancel`: hủy khi backend cho phép.

Các trạng thái nên ánh xạ thành: `SUBMITTED` (đã gửi), `PENDING_VERIFICATION` (chờ xác minh), `APPROVED` (đã xác nhận), `REJECTED` (từ chối), `RESOLVED` (đã xử lý), `CANCELLED` (đã hủy).

## Thanh toán phí mất thẻ bằng VNPay

Phí phạt cố định là **50.000 VND**, chỉ hiển thị sau khi nhân viên chuyển báo cáo sang `APPROVED`. Frontend không tự tính hoặc cho người dùng sửa số tiền; số tiền phải lấy từ backend.

1. Sau khi duyệt, hiển thị số tiền, mã báo cáo, hạn thanh toán và nút **Thanh toán qua VNPay**.
2. Gọi `POST /api/lost-card-reports/{id}/payments` để lấy `paymentUrl`, sau đó chuyển hướng người dùng sang VNPay.
3. Khi VNPay chuyển về frontend, chỉ hiển thị “đang xác minh thanh toán” và gọi `GET /api/payments/{transactionId}`. Không dựa vào query string trên trình duyệt để đánh dấu đã trả tiền.
4. Khi backend xác nhận `PAID`, hiển thị biên nhận và bước tiếp theo: xe trong bãi tiếp tục đối soát/checkout tại quầy; xe ngoài bãi được đăng ký thẻ tháng thay thế. Phí giữ xe (nếu có) hiển thị riêng.
5. Nếu `FAILED`, `EXPIRED` hoặc `CANCELLED`, cho phép thanh toán lại bằng giao dịch mới.

Không hiển thị nút cấp thẻ thay thế hoặc hoàn tất báo cáo khi phí mất thẻ chưa có trạng thái `PAID`.

Frontend không tự kết luận phí phạt, không tự mở barrier và không ẩn nút báo mất thẻ khi không tìm thấy phiên gửi xe. Phí, quyền xử lý và kết quả xác minh phải lấy từ response backend.

## Kiểm tra và lỗi

- Bắt buộc: bãi xe, biển số xe, phương thức xác minh và thời điểm mất thẻ hoặc `UNKNOWN`.
- Chuẩn hóa biển số trước khi gửi nhưng vẫn hiển thị giá trị người dùng đã nhập.
- Nếu backend trả `DUPLICATE_ACTIVE_REPORT`, mở báo cáo đang tồn tại thay vì tạo bản ghi mới.
- Nếu chưa có phiên gửi xe, hiển thị: “Yêu cầu đã được ghi nhận; nhân viên sẽ xác minh khi xe vào bãi hoặc qua thông tin tài khoản.”
- Với thẻ tháng và xe đang trong bãi, hiển thị mã yêu cầu cùng hướng dẫn liên hệ quầy; không cho người dùng tự kết thúc phiên.
- Với thẻ tháng và xe không trong bãi, hiển thị thao tác yêu cầu khóa thẻ và đăng ký thẻ thay thế nếu backend cho phép.
- Không dùng kết quả redirect từ VNPay làm bằng chứng thanh toán; trạng thái phải lấy lại từ backend sau khi IPN được xác minh.
- Chống gửi lặp bằng nút loading và `Idempotency-Key` cho mỗi lần submit.

## Acceptance criteria

- Xe chưa vào bãi vẫn tạo được báo cáo với `parkingSessionId = null`.
- Xe đã vào bãi vẫn tạo được báo cáo và liên kết phiên gửi xe nếu có.
- Người dùng xem được mã yêu cầu và trạng thái sau khi gửi.
- Không có logic frontend yêu cầu `entryTime`, `activeSession` hoặc `parkingSessionId` trước khi mở/submit biểu mẫu.
- Không thể tạo giao dịch thanh toán trước khi báo cáo được `APPROVED`.
- Không thể hoàn tất báo cáo hoặc cấp thẻ thay thế nếu thanh toán 50.000 VND chưa ở trạng thái `PAID`.
