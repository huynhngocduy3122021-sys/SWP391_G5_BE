# Hiển thị sức chứa cho Staff

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
