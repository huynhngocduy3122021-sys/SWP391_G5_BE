# 📷 Tài liệu các hàm quản lý ảnh (VehicleImageService)

Tài liệu này giải thích chi tiết hoạt động của các hàm liên quan đến việc xử lý hình ảnh phương tiện đỗ xe trong [VehicleImageService.java](file:///D:/Ki7/SWP391/Index/Index/src/main/java/Parking/Service/VehicleImageService.java).

---

## 🔑 1. `uploadVehicleImages(Long parkingSessionId, VehicleImageType imageType, List<MultipartFile> files)`

* **Chức năng:** Tiếp nhận các file ảnh tải lên từ nhân viên soát vé, đẩy lên Cloudinary và liên kết thông tin ảnh với phiên đỗ xe hiện tại.
* **Tại sao cần làm vậy?** 
  Khi xe vào/ra bãi, hệ thống camera hoặc nhân viên sẽ chụp ảnh biển số xe (`PLATE`) và ảnh tổng thể phương tiện (`VEHICLE`). Các ảnh này cần được lưu trữ bảo mật trên đám mây đám mây Cloudinary và lưu đường dẫn (URL) vào Database của dự án để đối chiếu khi có tranh chấp hoặc sự cố trộm cắp.
* **Quy trình hoạt động:**
  1. Kiểm tra danh sách file tải lên không được rỗng, giới hạn số lượng tối đa là **5 ảnh** một lần tải lên.
  2. Tìm kiếm phiên đỗ xe tương ứng theo ID (`parkingSessionId`).
  3. Định nghĩa thư mục lưu trữ trên Cloudinary theo cấu trúc ngăn nắp: `vinparking/parking-sessions/{sessionId}/{imageType}`.
  4. Lặp qua từng file hình ảnh:
     * Kiểm tra hợp lệ của file (kích thước file $\le 10$ MB, định dạng file phải bắt đầu bằng `image/`).
     * Gọi API Cloudinary tải ảnh lên dưới dạng mảng byte.
     * Thu thập địa chỉ URL bảo mật (`secure_url`) và ID tài nguyên quản lý trên Cloudinary (`public_id`).
     * Tạo mới đối tượng thực thể `VehicleImage` chứa các thông tin trên và đưa vào danh sách chờ lưu.
  5. Thực hiện lưu toàn bộ danh sách `VehicleImage` vào database thông qua phương thức `saveAllAndFlush`.
  6. **Cơ chế Rollback lỗi nâng cao (Transaction Rollback):**
     * Nếu có bất kỳ lỗi nào xảy ra trong khối try (lỗi tải file, lỗi database,...), luồng Catch sẽ được kích hoạt.
     * Hệ thống sẽ duyệt qua danh sách các ảnh đã tải lên Cloudinary thành công từ trước đó trong cùng phiên giao dịch này và gọi phương thức `deleteFromCloudinarySilently` để xóa chúng đi.
     * Điều này ngăn chặn việc xuất hiện các ảnh rác không có liên kết database nằm lơ lửng trên tài khoản Cloudinary của bạn, giúp tiết kiệm dung lượng lưu trữ cloud.
* **Đầu vào:**
  * `parkingSessionId`: ID phiên đỗ xe liên kết.
  * `imageType`: Loại ảnh (`PLATE` hoặc `VEHICLE`).
  * `files`: Danh sách các tệp tin MultipartFile nhận từ request.
* **Đầu ra:** `List<VehicleImageResponse>` (chứa danh sách URL ảnh vừa lưu).

---

## 🚪 2. `getImagesBySession(Long parkingSessionId)`

* **Chức năng:** Lấy toàn bộ danh sách hình ảnh đã lưu của một phiên đỗ xe cụ thể.
* **Tại sao cần làm vậy?** 
  Phục vụ giao diện kiểm soát ở cổng ra. Khi khách hàng quét thẻ ra, Frontend sẽ gọi API này để lấy hình ảnh lúc vào của chiếc xe này, hiển thị lên màn hình giúp nhân viên soát vé so sánh xem người điều khiển, phương tiện và biển số xe có khớp với hình ảnh thực tế lúc vào hay không.
* **Đầu vào:** `parkingSessionId` (ID phiên đỗ xe).
* **Đầu ra:** `List<VehicleImageResponse>` sắp xếp theo thời gian tải lên tăng dần.

---

## 🛠️ 3. Các hàm hỗ trợ nội bộ (Private Helpers)

### A. `validateImage(MultipartFile file)`
* **Chức năng:** Xác thực tệp tải lên: không được trống, kích thước tối đa 10MB và phải đúng định dạng hình ảnh (MimeType bắt đầu bằng `image/`).
* **Tại sao cần?** Ngăn chặn người dùng vô tình hoặc cố ý tải lên các file định dạng lạ, file chứa mã độc làm ảnh hưởng tới server lưu trữ.

### B. `deleteFromCloudinarySilently(String publicId)`
* **Chức năng:** Thực hiện xóa một bức ảnh trên Cloudinary dựa trên `publicId` của nó mà không phát ra ngoại lệ (exception) nếu xóa thất bại.
* **Tại sao cần?** Dùng trong khối catch để dọn dẹp các ảnh đã tải lên Cloudinary khi xảy ra lỗi lưu Database, đảm bảo an toàn cho tiến trình rollback.
