# Tổng hợp thay đổi Staff, doanh thu và chính sách giá

Tài liệu này tổng hợp toàn bộ thay đổi backend liên quan đến hiển thị sức chứa cho Staff, giới hạn dữ liệu doanh thu và quy tắc quản lý chính sách giá.

## Mục tiêu

Bổ sung API để Staff/Manager xem nhanh sức chứa của đúng chi nhánh được gán cho tài khoản đang đăng nhập.

## Các thay đổi

### API mới

- Method: `GET`
- URL: `/api/parking-branches/my-capacity`
- Phân quyền: `STAFF`, `MANAGER`
- Xác thực: gửi JWT qua header `Authorization: Bearer <token>`
- Không nhận `branchId` từ client. Backend tự lấy chi nhánh của tài khoản đăng nhập để tránh xem dữ liệu của chi nhánh khác.

Ví dụ response:

```json
{
  "parkingBranchId": 12,
  "branchName": "Chi nhánh Trung tâm",
  "totalCapacity": 100,
  "occupiedCapacity": 65,
  "reservedCapacity": 10,
  "availableCapacity": 25
}
```

Ý nghĩa các trường:

- `totalCapacity`: tổng sức chứa của các khu vực, tầng và chi nhánh đang hoạt động.
- `occupiedCapacity`: số phiên gửi xe đang ở trạng thái `ACTIVE`.
- `reservedCapacity`: số booking `CONFIRMED` vẫn còn thời gian giữ chỗ.
- `availableCapacity`: số chỗ còn lại, được tính bằng `max(0, totalCapacity - occupiedCapacity - reservedCapacity)`.

### Mã nguồn

- Thêm `ParkingCapacityResponse` làm DTO riêng cho màn hình sức chứa.
- Thêm `ParkingBranchService#getMyBranchCapacity()` và tái sử dụng một hàm tính sức chứa chung.
- Thêm `ParkingBranchController#getMyCapacity()` với `@PreAuthorize`.
- Dùng `BranchScopeService` để tự giới hạn dữ liệu theo chi nhánh của Staff/Manager.
- Thêm unit test cho phép tính thông thường và trường hợp số chỗ trống không được âm.

## Gợi ý tích hợp giao diện Staff

Khi mở dashboard hoặc màn hình check-in, gọi API trên và hiển thị tối thiểu:

- Tổng số chỗ: `totalCapacity`.
- Đang sử dụng: `occupiedCapacity`.
- Đã đặt trước: `reservedCapacity`.
- Còn trống: `availableCapacity`.

Nên tải lại dữ liệu sau mỗi lần check-in/check-out thành công hoặc theo chu kỳ phù hợp để số liệu trên màn hình luôn mới.

## Giới hạn doanh thu hiển thị cho Staff

API `GET /api/payments/all` đã được giới hạn cho các role `STAFF`, `MANAGER`, `ADMIN`.

Riêng khi người gọi là `STAFF`, response chỉ bao gồm:

- Payment có liên kết với `ParkingSession` (doanh thu phát sinh từ lượt gửi xe/check-out).
- ParkingSession thuộc đúng chi nhánh được gán cho tài khoản Staff.
- Không bao gồm payment liên kết với `MonthlyTicketRequest`, vì Staff không được xem doanh thu thẻ tháng.

Nếu tài khoản Staff chưa được gán chi nhánh, backend trả lỗi `Tài khoản chưa được gán chi nhánh` thay vì trả dữ liệu toàn hệ thống.

`MANAGER` và `ADMIN` vẫn giữ hành vi báo cáo hiện tại.

### Tiền phạt mất thẻ

- Khi checkout với `lostCard = true`, backend tính phí phạt mất thẻ là `50.000đ`.
- `Payment.amount` bằng `parkingFee + penaltyFee`, vì vậy payment lượt xe mà Staff nhìn thấy đã bao gồm tiền phạt.
- `ParkingSession` lưu riêng `parkingFee`, `penaltyFee` và `totalAmount`.
- `PaymentReportResponse` hiện chỉ trả `amount`; Staff thấy tiền phạt trong tổng tiền nhưng chưa có trường riêng để phân biệt phần phí gửi xe và phần tiền phạt.

### Lưu ý tích hợp frontend doanh thu Staff

Frontend `StaffTopbar.jsx` hiện vẫn tính doanh thu từ `GET /api/parking-sessions`. Backend chỉ trả session `ACTIVE` cho Staff, trong khi frontend cần session đã checkout, nên cách tính này có thể luôn cho kết quả `0đ`.

Để dùng dữ liệu payment đã được giới hạn an toàn theo chi nhánh, frontend cần chuyển sang gọi `GET /api/payments/all`. Không nên tự quyết định phạm vi dữ liệu bằng `branchId` trong `localStorage`.

## Giới hạn chính sách giá check-in/check-out

- Mỗi loại phương tiện chỉ được có một chính sách giá lượt dùng cho check-in/check-out.
- Quy tắc áp dụng cả khi chính sách hiện có đang active hoặc inactive; Admin phải chỉnh sửa chính sách hiện có thay vì tạo bản ghi mới.
- Khi cập nhật, Admin vẫn được sửa chính sách hiện tại hoặc đổi loại xe nếu loại xe đích chưa có chính sách giá lượt.
- Chính sách gói (tên chứa `gói` hoặc `tháng`) được quản lý riêng và không bị tính là chính sách giá check-in/check-out.
- Mỗi loại phương tiện cũng chỉ được có một gói đăng ký, bất kể gói hiện tại đang active hay inactive.
- Nếu đã có gói, Admin phải chỉnh sửa gói hiện tại; không được tạo thêm gói mới hoặc chuyển gói khác vào cùng loại xe.
- Các thao tác tạo, cập nhật và xóa chính sách giá chỉ dành cho `ADMIN`.

### Cách phân loại chính sách

- Tên chứa `gói` hoặc `tháng`, không phân biệt chữ hoa/chữ thường: gói đăng ký.
- Các tên còn lại: chính sách giá lượt check-in/check-out.
- Đây là quy tắc tương thích với dữ liệu hiện tại vì model chưa có trường enum phân loại chính sách.

### Validation khi tạo và cập nhật

Khi tạo mới:

- Nếu loại xe đã có giá lượt, từ chối tạo thêm giá lượt.
- Nếu loại xe đã có gói đăng ký, từ chối tạo thêm gói.
- Giá lượt không chặn gói đăng ký và gói đăng ký không chặn giá lượt.

Khi cập nhật:

- Cho phép sửa chính bản ghi hiện tại.
- Nếu đổi loại phương tiện, loại phương tiện đích không được có chính sách khác cùng nhóm.
- Việc đổi tên làm chính sách chuyển từ nhóm giá lượt sang nhóm gói hoặc ngược lại cũng phải thỏa mãn quy tắc không trùng của nhóm đích.

Thông báo khi tạo hoặc cập nhật trùng:

```text
Loại phương tiện này đã có chính sách giá check-in/check-out. Vui lòng chỉnh sửa chính sách hiện có.
```

hoặc:

```text
Loại phương tiện này đã có gói đăng ký. Vui lòng chỉnh sửa chính sách hiện có.
```

### Dữ liệu cũ

Validation ngăn dữ liệu trùng mới nhưng không tự động xóa các chính sách trùng đã tồn tại trong database. Việc giữ nguyên dữ liệu cũ nhằm tránh xóa nhầm thông tin giá; Admin cần kiểm tra và xử lý các bản ghi trùng hiện có.

## Danh sách file mã nguồn liên quan

- `src/main/java/Parking/Controller/ParkingBranchController.java`
- `src/main/java/Parking/Controller/PaymentController.java`
- `src/main/java/Parking/Controller/PricePolicyController.java`
- `src/main/java/Parking/Repository/PricePolicyRepository.java`
- `src/main/java/Parking/Service/ParkingBranchService.java`
- `src/main/java/Parking/Service/PaymentService.java`
- `src/main/java/Parking/Service/PricePolicyService.java`
- `src/main/java/Parking/dto/response/ParkingCapacityResponse.java`
- `src/test/java/Parking/Service/ParkingBranchServiceTest.java`
- `src/test/java/Parking/Service/PaymentServiceMonthlyTicketTest.java`
- `src/test/java/Parking/Service/PricePolicyServiceTest.java`

## Kiểm thử

Các trường hợp đã được kiểm tra gồm:

- Tính sức chứa và không trả số chỗ trống âm.
- Staff chỉ nhận payment lượt xe thuộc chi nhánh của mình và không nhận payment vé tháng.
- Chặn tạo giá lượt thứ hai cho cùng loại xe.
- Cho phép chỉnh sửa chính sách giá lượt hiện tại.
- Giá lượt và gói đăng ký không chặn chéo nhau.
- Chặn tạo gói đăng ký thứ hai cho cùng loại xe.
- Cho phép chỉnh sửa gói đăng ký hiện tại.

Kết quả lần chạy gần nhất: `13` test, `0` failure, `0` error, `BUILD SUCCESS`.
