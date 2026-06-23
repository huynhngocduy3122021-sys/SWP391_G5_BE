# 🏢 Tài liệu các hàm quản lý tài sản bãi xe (Other Management Services)

Tài liệu này giải thích hoạt động của các hàm nghiệp vụ quản trị cơ sở vật chất (Chi nhánh, Tầng, Khu vực, Vị trí đỗ xe) và quản lý xe trong hệ thống.

---

## 🏛️ 1. Quản lý Chi nhánh (ParkingBranchService)
* **`getAllActiveBranches()`**: Lấy danh sách các chi nhánh bãi xe đang ở trạng thái hoạt động (`active = true`). Phục vụ giao diện cho khách hoặc ứng dụng đăng ký chỗ gửi xe.
* **`createBranch(ParkingBranchRequest request)`**: Tạo chi nhánh mới.
* **`updateBranch(Long branchId, ParkingBranchRequest request)`**: Cập nhật tên, địa chỉ, thông tin mô tả chi nhánh.
* **`toggleBranchStatus(Long branchId)`**: Đóng cửa tạm thời hoặc mở cửa lại một chi nhánh (chuyển trạng thái `active`). Khi đóng chi nhánh, các thẻ gửi xe thuộc chi nhánh đó cũng không dùng để check-in được nữa.

---

## 📶 2. Quản lý Tầng đỗ xe (ParkingFloorService)
* **`getFloorsByBranch(Long branchId)`**: Lấy toàn bộ danh sách tầng thuộc một chi nhánh.
* **`createFloor(ParkingFloorRequest request)`**: Thêm tầng mới (ví dụ: Tầng hầm B1, Tầng 1, Tầng 2).
* **`updateFloor(...)` / `deleteFloor(...)`**: Chỉnh sửa hoặc xóa thông tin tầng đỗ.
* **Tại sao phân tầng?** Bãi gửi xe thông minh cần biết chính xác xe đang đỗ ở khu vực của tầng nào để hiển thị sơ đồ và hướng dẫn khách tìm xe.

---

## 🗺️ 3. Quản lý Khu vực đỗ xe (ParkingZoneService)
* **`getZonesByFloor(Long floorId)`**: Truy xuất danh sách phân khu thuộc một tầng đỗ xe cụ thể.
* **`createZone(ParkingZoneRequest request)`**: Tạo phân khu mới (Ví dụ: Khu A - đỗ xe máy, Khu B - đỗ ô tô).
* **`calculateCapacity(Long zoneId)`**: Tính toán sức chứa hiện tại của phân khu dựa trên số lượng ô đỗ cụ thể được khai báo hoạt động trong đó.

---

## 🅿️ 4. Quản lý Vị trí đỗ (SlotService)
* **`getAvailableSlotsByBranchAndVehicleType(Long branchId, Long vehicleTypeId)`**:
  * **Chức năng:** Tìm kiếm các ô đỗ còn trống trong một chi nhánh cho loại xe tương ứng.
  * **Tại sao làm vậy?** Để hỗ trợ các thuật toán điều phối thông minh, tự động chỉ dẫn xe máy đi vào khu xe máy còn trống hoặc xe ô tô đi vào ô trống cụ thể.
* **`changeSlotStatus(Long slotId, SlotStatus status)`**: Cập nhật trạng thái ô đỗ (ví dụ: chuyển từ trống `AVAILABLE` sang đã có xe `OCCUPIED` hoặc đang sửa chữa `MAINTENANCE`).

---

## 🚘 5. Quản lý Phương tiện (VehicleService)
* **`registerVehicle(VehicleRequest request, Long userId)`**: 
  * **Chức năng:** Cho phép khách hàng đăng ký trước thông tin xe cá nhân lên hệ thống trước khi tới bãi.
  * **Tại sao cần?** Tiết kiệm thời gian check-in. Khi xe đến cổng, hệ thống camera quét biển số, đối chiếu thấy thông tin xe đã đăng ký sẵn sẽ tự động khớp thẻ gửi xe nhanh chóng mà không cần nhập thủ công.
* **`getVehiclesByUser(Long userId)`**: Lấy danh sách xe đã đăng ký của một người dùng.
* **`updateVehicle(...)` / `deleteVehicle(...)`**: Sửa đổi thông tin xe (Đổi biển số mới, đổi màu xe) hoặc xóa xe cũ khỏi tài khoản.
