# 🛠️ Hướng dẫn Cài đặt & Khởi chạy (Local Setup)

Tài liệu này hướng dẫn cách cấu hình cơ sở dữ liệu, thiết lập biến môi trường và chạy ứng dụng Spring Boot trên máy tính cá nhân.

---

## 📋 Yêu cầu hệ thống (Prerequisites)

* **Java Development Kit (JDK):** Phiên bản **17** trở lên.
* **Cơ sở dữ liệu:** Microsoft SQL Server.
* **Build Tool:** Apache Maven.
* **IDE Khuyên dùng:** IntelliJ IDEA (Community hoặc Ultimate) hoặc VS Code.
* **Công cụ bổ trợ:** Ngrok (dành cho kiểm thử VNPay Sandbox).

---

## 🗃️ Cấu hình Cơ sở dữ liệu

1. Mở phần mềm quản trị SQL Server (SSMS hoặc Azure Data Studio).
2. Tạo mới một cơ sở dữ liệu có tên trùng khớp cấu hình dự án:
   ```sql
   CREATE DATABASE parking_system;
   ```
3. Mở file [application.properties](file:///D:/Ki7/SWP391/Index/Index/src/main/resources/application.properties) và kiểm tra lại thông tin tài khoản kết nối:
   ```properties
   spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=parking_system;encrypt=true;trustServerCertificate=true
   spring.datasource.username=sa   # Thay bằng tài khoản SQL Server của bạn
   spring.datasource.password=12345 # Thay bằng mật khẩu SQL Server của bạn
   ```

---

## 🚀 Khởi chạy dự án bằng PowerShell Script (Nhanh nhất)

Mình đã viết sẵn file script [run.ps1](file:///D:/Ki7/SWP391/Index/Index/run.ps1) ở thư mục gốc của dự án. Script này sẽ tự động khai báo các thông tin bảo mật của Cloudinary, cấu hình VNPay và chạy lệnh Maven khởi động server.

### Hướng dẫn chạy:

1. Mở cửa sổ **PowerShell** tại thư mục gốc của dự án (hoặc dùng Terminal tích hợp của IntelliJ/VS Code).
2. Do Windows chặn chạy file script mặc định, hãy cấp quyền chạy script cho phiên làm việc hiện tại:
   ```powershell
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
   ```
3. Chạy file script:
   ```powershell
   .\run.ps1
   ```

---

## 🌐 Cấu hình Chạy kiểm thử VNPay Sandbox

Vì máy chủ VNPay Sandbox không thể gửi dữ liệu trực tiếp về cổng `localhost:8081` trên máy của bạn, bạn cần sử dụng **Ngrok** để tạo kết nối Public:

1. Mở một cửa sổ Terminal mới trên máy tính.
2. Khởi chạy Ngrok để chuyển tiếp cổng `8081`:
   * **Nếu bạn sử dụng tên miền tĩnh (Static Domain) của nhóm:**
     ```bash
     ngrok http 8081 --domain=bullpen-viewer-overfill.ngrok-free.dev
     ```
   * **Nếu bạn sử dụng tài khoản miễn phí thông thường (Dynamic Domain):**
     ```bash
     ngrok http 8081
     ```
     *Lưu ý:* Khi chạy lệnh này, Ngrok sẽ hiển thị một đường link ngẫu nhiên (ví dụ: `https://abcd-123.ngrok-free.dev`). Bạn cần copy link này và cập nhật lại biến môi trường `VNPAY_RETURN_URL` và `VNPAY_IPN_URL` trong file [run.ps1](file:///D:/Ki7/SWP391/Index/Index/run.ps1) trước khi chạy dự án.

---

## 📖 Kiểm thử API qua Swagger UI

Sau khi dự án đã chạy thành công (cổng `8081`), bạn có thể mở trình duyệt và truy cập đường dẫn sau để xem tài liệu API chi tiết và chạy thử nghiệm trực tuyến các Endpoint:

👉 [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
