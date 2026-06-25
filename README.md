# 🚗 Parking Management System Backend

Chào mừng đến với mã nguồn Backend của dự án quản lý bãi đỗ xe thông minh. Hệ thống được xây dựng trên nền tảng **Spring Boot 3** và **SQL Server**, cung cấp trọn gói các API quản lý chi nhánh bãi xe, giám sát xe ra vào và tích hợp thanh toán điện tử VNPay.

---

## 📖 Tài liệu hướng dẫn chi tiết (Documentation)

Để giúp các thành viên trong nhóm nhanh chóng nắm bắt và vận hành dự án, vui lòng đọc các tài liệu hướng dẫn chi tiết dưới đây:

* **[🏗️ Hướng dẫn Kiến trúc Dự án](file:///D:/Ki7/SWP391/Index/Index/docs/01_ARCHITECTURE.md):** Giải thích về cấu hình thư mục, mô hình 3 lớp, luồng dữ liệu (Data Flow) và các công nghệ sử dụng trong dự án.
* **[🗄️ Thiết kế Cơ sở dữ liệu (ERD)](file:///D:/Ki7/SWP391/Index/Index/docs/02_DATABASE.md):** Xem sơ đồ quan hệ thực thể (Database ERD Diagram), mô tả chi tiết các bảng từ Tầng/Khu vực đỗ xe tới Thẻ gửi xe và Giao dịch thanh toán.
* **[🔄 Luồng Nghiệp vụ chính](file:///D:/Ki7/SWP391/Index/Index/docs/03_WORKFLOWS.md):** Mô tả chi tiết bằng sơ đồ Sequence quy trình Check-in (xe vào), Check-out (xe ra & tính phí) và cơ chế tích hợp cổng VNPay Sandbox.
* **[🛠️ Hướng dẫn Cài đặt & Khởi chạy (Local Setup)](file:///D:/Ki7/SWP391/Index/Index/docs/04_SETUP.md):** Hướng dẫn từng bước cấu hình SQL Server, dùng script `run.ps1` để nạp biến môi trường tự động, chạy cổng phụ ngrok và tài liệu kiểm thử trực quan Swagger UI.

---

### 📂 Tài liệu chi tiết các Hàm & Nghiệp vụ (Function Reference)

* **[🚗 Lượt gửi xe (ParkingSessionService)](file:///D:/Ki7/SWP391/Index/Index/docs/05_FUNCTIONS_PARKING_SESSION.md):** Giải thích quy trình check-in xe vào, check-out xe ra, tìm kiếm và phân tích logic.
* **[💳 Tính phí & Thanh toán VNPay (Payment & VNPay Services)](file:///D:/Ki7/SWP391/Index/Index/docs/06_FUNCTIONS_PAYMENT_VNPAY.md):** Xem cách tính giá vé gửi xe (làm tròn giờ Math.ceil), tạo cổng liên kết VNPay QR, và luồng gọi ngầm bảo mật IPN.
* **[📷 Quản lý ảnh đỗ xe (VehicleImageService)](file:///D:/Ki7/SWP391/Index/Index/docs/07_FUNCTIONS_VEHICLE_IMAGES.md):** Giải thích cách lưu trữ ảnh lên mây Cloudinary và cơ chế tự động dọn dẹp ảnh rác khi rollback database.
* **[🔐 Đăng ký, Đăng nhập & Mã hóa Token JWT (User & Token Services)](file:///D:/Ki7/SWP391/Index/Index/docs/07_FUNCTIONS_USER_AUTH.md):** Giải thích quy trình mã hóa mật khẩu BCrypt và cơ chế xác thực JWT của hệ thống.
* **[🏢 Quản lý hạ tầng & Phương tiện (Other Services)](file:///D:/Ki7/SWP391/Index/Index/docs/08_FUNCTIONS_OTHER_SERVICES.md):** Hướng dẫn về các dịch vụ quản lý chi nhánh, tầng, phân khu đỗ xe, vị trí trống và thông tin xe đăng ký trước.

---

## ⚡ Khởi chạy nhanh (Quick Start)

Dành cho những ai muốn chạy thử ngay dự án mà không cần đọc nhiều:

1. **Khởi tạo database:** Tạo database trống tên là `parking_system` trong SQL Server.
2. **Khởi chạy Tunnel Ngrok (để test VNPay):**
   ```bash
   ngrok http 8081 --domain=bullpen-viewer-overfill.ngrok-free.dev
   ```
3. **Cấp quyền & Khởi động dự án:** Mở cửa sổ PowerShell trong thư mục này và chạy:
   ```powershell
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
   .\run.ps1
   ```
4. **Kiểm tra API:** Truy cập [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html) để bắt đầu kiểm thử.
