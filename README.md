# TÀI LIỆU TỔNG QUAN DỰ ÁN SWP391_G5_BE (HỆ THỐNG QUẢN LÝ BÃI ĐỖ XE)

Đây là tài liệu giới thiệu tổng quan về cấu trúc, tính năng và công nghệ sử dụng trong backend dự án Hệ thống quản lý bãi đỗ xe (Parking Management System) được xây dựng bằng **Spring Boot**.

---

## 1. MỤC TIÊU DỰ ÁN
Dự án cung cấp một hệ thống API mạnh mẽ để quản lý toàn diện các hoạt động của một hoặc nhiều chi nhánh bãi đỗ xe, từ việc quản lý xe ra/vào, vé tháng, thanh toán, nhận diện biển số cho đến việc xử lý sự cố.

---

## 2. CÔNG NGHỆ SỬ DỤNG
* **Framework chính:** Spring Boot (Java)
* **Kiến trúc:** RESTful API, MVC (Model-View-Controller)
* **Bảo mật:** Spring Security, JWT (JSON Web Token) cho Authentication/Authorization.
* **Cơ sở dữ liệu:** Thao tác qua Spring Data JPA / Hibernate.
* **Tích hợp bên thứ 3:** Cloudinary (Lưu trữ hình ảnh/video), AI Nhận diện biển số xe (LPR - License Plate Recognition).
* **Tài liệu API:** Swagger UI (OpenAPI 3).

---

## 3. CẤU TRÚC THƯ MỤC DỰ ÁN (src/main/java/Parking)

Dự án áp dụng mô hình phân tầng tiêu chuẩn của Spring Boot:

1. **`/config`**: Chứa các cấu hình của hệ thống.
   * *Ví dụ:* `CloudinaryConfig` (Lưu trữ ảnh), cấu hình Security, cấu hình Swagger, v.v.
   
2. **`/Controller`**: Lớp ngoài cùng tiếp nhận các Request từ Frontend (React/Vue/Mobile).
   * Chứa các endpoint API chia theo từng chức năng (User, Vehicle, Ticket...).
   
3. **`/Service`**: Lớp xử lý Logic nghiệp vụ (Business Logic).
   * Tại đây dữ liệu từ Controller sẽ được kiểm tra, tính toán trước khi gọi xuống Database.

4. **`/Repository`**: Lớp giao tiếp trực tiếp với cơ sở dữ liệu (Database).
   * Kế thừa `JpaRepository` để thực hiện các thao tác CRUD (Thêm, sửa, xóa, tìm kiếm).
   
5. **`/Model`**: Chứa các Entity class map 1-1 với các bảng trong Database.
   * Gồm các bảng như: `User`, `Vehicle`, `ParkingCard`, `MonthlyTicket`...
   
6. **`/dto` (Data Transfer Object)**: Chứa các class trung gian để vận chuyển dữ liệu.
   * Giúp ẩn đi cấu trúc thực sự của Entity và chỉ trả về/nhận vào những dữ liệu Frontend cần. Chia làm `request` (nhận vào) và `response` (trả về).
   
7. **`/enums`**: Các biến hằng số, trạng thái (Ví dụ: `AVAILABLE`, `IN_USE`...).
8. **`/exception`**: Xử lý lỗi tập trung, trả về các thông báo lỗi chuẩn cho Frontend.
9. **`/Util`**: Các hàm tiện ích dùng chung (Ví dụ: format ngày tháng, mã hóa mật khẩu...).

---

## 4. CÁC TÍNH NĂNG & MODULE CHÍNH

Dựa trên cấu trúc file, hệ thống bao gồm các Module chính sau:

### 4.1. Quản lý Người dùng & Phân quyền (Auth & User)
* Đăng ký, Đăng nhập (Authentication) bằng JWT (`AuthController`).
* Phân quyền truy cập (Authorization) cho các role: `USER` (Khách), `STAFF` (Nhân viên), `MANAGER` (Quản lý), `ADMIN`.

### 4.2. Quản lý Hạ tầng Bãi đỗ (Infrastructure)
* Quản lý các chi nhánh bãi đỗ xe (`ParkingBranchController`).
* Quản lý các tầng trong bãi đỗ (`ParkingFloorController`).
* Quản lý các khu vực đỗ xe chuyên biệt (`ParkingZoneController`).

### 4.3. Quản lý Xe & Hình ảnh (Vehicle)
* Đăng ký phương tiện của khách hàng (`VehicleController`).
* Phân loại phương tiện (Xe máy, Xe ô tô...) (`VehicleTypeController`).
* Quản lý hình ảnh phương tiện, đồng bộ lên Cloudinary (`VehicleImageController`).

### 4.4. Quản lý Vé & Thẻ giữ xe (Ticketing & Card)
* Cấp phát và quản lý thẻ vật lý/thẻ từ (`ParkingCardController`).
* Khách hàng gửi yêu cầu đăng ký vé tháng (`MonthlyTicketRequestController`).
* Nhân viên duyệt/quản lý vé tháng (`MonthlyTicketController`).

### 4.5. Quản lý Hoạt động Gửi xe (Operations)
* **Phiên gửi xe (Parking Session):** Quản lý quy trình xe vào / xe ra (`ParkingSessionController`).
* **Nhận diện biển số (LPR):** Tích hợp AI để đọc biển số tự động khi xe qua cổng (`LicensePlateRecognitionController`).
* **Đặt chỗ trước (Booking):** Cho phép khách hàng đặt slot đậu xe trước (`BookingController`).

### 4.6. Tài chính & Thanh toán (Finance)
* Thiết lập chính sách giá cho từng loại xe, từng khung giờ (`PricePolicyController`).
* Quản lý thanh toán vé lượt, vé tháng (`PaymentController`).

### 4.7. Quản lý Sự cố (Incident Management)
* Ghi nhận và xử lý các sự cố xảy ra trong bãi đỗ (Mất xe, quẹt xe, hỏng thẻ...) (`IncidentReportController`).

---

## 5. LUỒNG HOẠT ĐỘNG CƠ BẢN (Ví dụ quy trình xe vào)
1. **Camera chụp ảnh** -> Gửi request lên `LicensePlateRecognitionController`.
2. Hệ thống bóc tách biển số.
3. Chuyển thông tin cho `ParkingSessionController` tạo một phiên gửi xe (Session).
4. Lưu thông tin (ảnh, giờ vào, biển số) xuống Database qua lớp `Service` và `Repository`.
5. Frontend nhận phản hồi thành công và mở barrier.

---

Tài liệu này đóng vai trò như một bản đồ giúp các lập trình viên nắm bắt nhanh cách hệ thống được cấu trúc và các chức năng hiện có. Khi cần chỉnh sửa chức năng nào, bạn chỉ cần tìm đến Controller tương ứng và trace (dò theo) luồng Controller -> Service -> Repository.
