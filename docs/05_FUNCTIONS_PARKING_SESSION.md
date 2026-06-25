# 📂 Tài liệu các hàm trong ParkingSessionService

Tệp tin này giải thích chi tiết hoạt động, đầu vào, đầu ra và lý do thiết kế của các hàm trong lớp [ParkingSessionService.java](file:///D:/Ki7/SWP391/Index/Index/src/main/java/Parking/Service/ParkingSessionService.java).

---

## 🔑 1. `guestCheckIn(GuestCheckInRequest request)`

* **Chức năng:** Xử lý toàn bộ quy trình cho khách vãng lai gửi xe vào bãi đỗ.
* **Tại sao cần hàm này?**
  Khi một chiếc xe đi tới cổng vào, nhân viên cần quét thẻ vật lý và ghi nhận thông tin xe (biển số, loại xe). Hàm này thực hiện hàng loạt kiểm tra logic nghiệp vụ để đảm bảo xe được đưa vào bãi đỗ một cách hợp lệ, tránh thất thoát thẻ hoặc đỗ xe quá tải.
* **Quy trình hoạt động bên trong:**
  1. Chuẩn hóa biển số xe và mã thẻ (chuyển chữ hoa, xóa dấu cách).
  2. Tìm kiếm thẻ gửi xe (`ParkingCard`) trong database. Đảm bảo thẻ ở trạng thái sẵn sàng sử dụng (`AVAILABLE`).
  3. Kiểm tra xem thẻ có thuộc một chi nhánh (`ParkingBranch`) đang hoạt động (`active = true`) hay không.
  4. Tìm thông tin loại xe (`VehicleType`) và tìm hoặc tạo mới bản ghi xe (`Vehicle`).
  5. Đảm bảo chiếc xe này hiện **chưa có phiên đỗ xe nào đang hoạt động** (tránh trường hợp một xe đỗ 2 lần đồng thời).
  6. Đảm bảo chiếc thẻ này hiện **không gắn với phiên đỗ xe nào đang hoạt động**.
  7. Tính toán tổng sức chứa (`totalCapacity`) của chi nhánh đối với loại xe này.
  8. Đếm số lượng xe thuộc loại này đang đỗ. Nếu đã đạt tối đa sức chứa, hệ thống chặn lại và báo lỗi "Bãi xe đã đầy".
  9. Tạo mới đối tượng `ParkingSession`, đặt thời gian `checkInTime` là thời gian hiện tại, trạng thái là `ACTIVE`.
  10. Đổi trạng thái thẻ vật lý thành `IN_USE` để tránh tái sử dụng thẻ khi chưa Check-out.
  11. Lưu tất cả thay đổi và trả về DTO Response.
* **Đầu vào:** `GuestCheckInRequest` (chứa `licensePlate`, `cardCode`, `vehicleTypeId`, `vehicleColor`, `vehicleBrand`).
* **Đầu ra:** `ParkingSessionResponse` (thông tin phiên gửi xe vừa được tạo).

---

## 🚪 2. `guestCheckOut(GuestCheckOutRequest request, String clientIp)`

* **Chức năng:** Tiếp nhận yêu cầu cho xe ra khỏi bãi đỗ.
* **Tại sao cần hàm này?**
  Khi xe ra đến cổng, nhân viên quét thẻ. Hệ thống cần đối chiếu biển số lúc vào và lúc ra để xác minh tính chính danh (phòng chống trộm cắp xe), sau đó tính toán chi phí để thanh toán.
* **Quy trình hoạt động bên trong:**
  1. Chuẩn hóa mã thẻ và biển số xe ở cổng ra.
  2. Tìm phiên gửi xe (`ParkingSession`) đang có trạng thái `ACTIVE` liên kết với thẻ gửi xe này.
  3. Đối chiếu biển số lưu trữ trong database (`storedLicensePlate`) với biển số lúc ra cổng (`exitLicensePlate`). Nếu không khớp, quăng lỗi an ninh (`ParkingSessionException`).
  4. Chuyển giao tiến trình thanh toán và tính phí sang `PaymentService.processCheckOutPayment` để hoàn tất.
* **Đầu vào:**
  * `GuestCheckOutRequest` (chứa `cardCode`, `licensePlate`, `paymentMethod`).
  * `clientIp` (địa chỉ IP máy khách, cần thiết nếu thanh toán qua VNPay).
* **Đầu ra:** `GuestCheckOutResponse` (chứa URL thanh toán nếu là VNPay, hoặc kết quả thanh toán ngay nếu là tiền mặt).

---

## 📊 3. `getAllParkingSession()`

* **Chức năng:** Lấy toàn bộ danh sách lịch sử và các phiên gửi xe đang hoạt động trong hệ thống.
* **Tại sao cần hàm này?**
  Phục vụ giao diện Dashboard quản trị hoặc phần mềm của nhân viên để theo dõi tổng quan bãi xe.
* **Đầu vào:** Không có.
* **Đầu ra:** `List<ParkingSessionResponse>` (Danh sách DTO).

---

## 🛠️ 4. Các hàm bổ trợ (Private Helper Methods)

### A. `createGuestVehicle(GuestCheckInRequest request, String licensePlate, VehicleType vehicleType)`
* **Chức năng:** Tạo mới thông tin xe của khách vãng lai và lưu vào DB nếu xe này lần đầu vào bãi.
* **Tại sao cần?** Khách vãng lai chưa đăng ký tài khoản từ trước, hệ thống cần tự động tạo thông tin phương tiện để đối chiếu lúc ra.

### B. `convertToResponse(ParkingSession parkingSession)`
* **Chức năng:** Ánh xạ từ thực thể cơ sở dữ liệu `ParkingSession` sang đối tượng DTO `ParkingSessionResponse`.
* **Tại sao cần?** Bảo mật dữ liệu, tránh phơi bày các liên kết Hibernate Entity phức tạp trực tiếp lên API, đồng thời định dạng lại các trường cho Frontend dễ hiển thị.

### C. Các hàm chuẩn hóa chữ (`normalizeLicensePlate`, `normalizeCardCode`, `normalizeOptionalText`)
* **Chức năng:** Cắt bỏ khoảng trắng dư thừa, chuyển thành chữ hoa.
* **Tại sao cần?** Tránh lỗi so khớp chuỗi trong database (ví dụ: Biển số xe `29A-123.45` và `29a - 123.45` phải được nhận diện là một).
