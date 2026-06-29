# Hướng dẫn tích hợp API Đặt chỗ (Booking) và Check-In cho Frontend (FE)

Tài liệu này cung cấp danh sách chi tiết các API cần thiết để Frontend tích hợp tính năng Đặt chỗ đỗ xe trước (Booking) và quy trình Check-in liên quan.

* **Base URL**: `http://localhost:8081` (hoặc cấu hình cổng chạy thực tế của Backend)
* **Định dạng dữ liệu**: `application/json`
* **Xác thực**: Một số API yêu cầu đính kèm Header `Authorization: Bearer <JWT_TOKEN>`.

---

## 1. Luồng dành cho Khách hàng (Customer App)

### 1.1. Tạo mới đặt chỗ (Create Booking)
Khách hàng chọn chi nhánh, loại xe, biển số xe và thời gian hẹn đến để giữ chỗ trước.

* **Method**: `POST`
* **Path**: `/api/bookings`
* **Headers**:
  * `Authorization`: `Bearer <JWT_TOKEN>` (Bắt buộc)
  * `Content-Type`: `application/json`
* **Request Body** (`CreateBookingRequest`):
  ```json
  {
    "parkingBranchId": 1,
    "vehicleTypeId": 3,
    "licensePlate": "30F-12345",
    "expectedArrivalTime": "2026-06-27T15:30:00",
    "vehicleColor": "Trắng",
    "vehicleBrand": "VinFast"
  }
  ```
  *(Lưu ý: Định dạng thời gian `expectedArrivalTime` là `YYYY-MM-DDTHH:mm:ss`, hai trường `vehicleColor` và `vehicleBrand` là tùy chọn không bắt buộc)*

* **Response (201 Created)**:
  ```json
  {
    "bookingId": 1,
    "bookingCode": "BK12345678",
    "userId": 9,
    "userFullName": "Nguyễn Văn A",
    "parkingBranchId": 1,
    "parkingBranchName": "Sieu Thi Sieu To",
    "vehicleId": 12,
    "licensePlate": "30F-12345",
    "vehicleTypeId": 3,
    "vehicleTypeName": "CAR",
    "parkingSessionId": null,
    "expectedArrivalTime": "2026-06-27T15:30:00",
    "holdUntil": "2026-06-27T16:00:00",
    "status": "CONFIRMED",
    "createdAt": "2026-06-27T13:30:00",
    "updatedAt": "2026-06-27T13:30:00",
    "cancelledAt": null,
    "completedAt": null,
    "expiredAt": null
  }
  ```
* **Lưu ý quan trọng**: FE cần sử dụng trường `bookingCode` trong Response để sinh mã QR Code (hoặc Barcode) hiển thị cho khách hàng dùng lúc check-in tại bãi.

---

### 1.2. Lấy danh sách đặt chỗ của tôi (My Bookings)
Hiển thị danh sách lịch sử đặt chỗ đỗ xe của khách hàng đang đăng nhập.

* **Method**: `GET`
* **Path**: `/api/bookings/my-bookings`
* **Headers**:
  * `Authorization`: `Bearer <JWT_TOKEN>` (Bắt buộc)
* **Response (200 OK)**: Trả về một mảng danh sách các đối tượng Booking (giống cấu trúc phần 1.1).

---

### 1.3. Hủy đặt chỗ (Cancel Booking)
Khách hàng hủy chỗ đỗ đã đặt trước (chỉ được hủy khi trạng thái là `CONFIRMED` hoặc `PENDING`).

* **Method**: `POST`
* **Path**: `/api/bookings/{bookingId}/cancel`
* **Headers**:
  * `Authorization`: `Bearer <JWT_TOKEN>` (Bắt buộc)
* **Response (200 OK)**: Trả về đối tượng Booking đã được cập nhật trạng thái `status: "CANCELLED"`.

---

## 2. Luồng dành cho Nhân viên / Máy Kiosk tại Cổng (Staff App)

Quy trình Check-in cho xe đã đặt trước gồm 3 bước:
1. Nhân viên quét mã QR lấy `bookingCode`, hệ thống tra cứu thông tin xe.
2. Nhân viên quẹt thẻ từ vật lý và gửi yêu cầu hoàn tất Check-In.
3. Camera chụp hình ảnh xe và gọi API tải ảnh lên liên kết với phiên gửi xe.

### 2.1. Bước 1: Tra cứu thông tin đặt chỗ bằng mã Booking
Dùng khi khách hàng đưa mã QR chứa `bookingCode` ra để quét tại cổng. Nhân viên quét mã để kiểm tra thông tin xe đỗ.

* **Method**: `GET`
* **Path**: `/api/bookings/code/{bookingCode}`
* **Headers**:
  * `Authorization`: `Bearer <JWT_TOKEN>` (Bắt buộc - dành cho tài khoản Staff/Admin)
* **Response (200 OK)**: Trả về thông tin đặt chỗ chi tiết. FE dùng thông tin này để kiểm tra xem biển số xe trên camera có khớp với biển số xe đặt trước hay không.

---

### 2.2. Bước 2: Thực hiện Check-In tạo phiên đỗ xe
Sau khi đối khớp thông tin xe thành công, nhân viên lấy 1 thẻ từ trống, quét mã thẻ từ đó và gửi yêu cầu Check-in.

* **Method**: `POST`
* **Path**: `/api/parking-sessions/booking/check-in`
* **Query Params**:
  * `bookingCode`: Mã đặt chỗ (ví dụ: `BK12345678`)
  * `cardCode`: Mã thẻ từ vật lý phát cho khách (ví dụ: `CARD0092`)
* **Headers**: Không yêu cầu Authorization (Public API dành cho cổng).
* **Response (200 OK)**:
  ```json
  {
    "parkingSessionId": 5,
    "checkInTime": "2026-06-27T14:15:00",
    "checkOutTime": null,
    "totalAmount": null,
    "status": "ACTIVE",
    "licensePlate": "30F-12345",
    "cardCode": "CARD0092",
    "branchName": "Sieu Thi Sieu To",
    "vehicleImageIds": [],
    "imageUrls": []
  }
  ```

---

### 2.3. Bước 3: Tải ảnh xe lúc vào bãi lên hệ thống (Upload Check-In Images)
Ngay sau khi bước 2 trả về kết quả thành công, Client (hoặc phần mềm camera tự động) lấy trường `parkingSessionId` từ kết quả bước 2 để tải ảnh chụp xe lúc check-in lên Cloud.

* **Method**: `POST`
* **Path**: `/api/parking-session/{parkingSessionId}/images`
* **Query Params**:
  * `imageType`: `CHECK_IN` (Bắt buộc đối với lúc vào bãi)
* **Headers**:
  * `Content-Type`: `multipart/form-data`
* **Body** (Form-Data):
  * `file`: (Chọn một hoặc nhiều tệp hình ảnh xe chụp tại barrier)
* **Response (201 Created)**: Trả về danh sách hình ảnh đã lưu trữ thành công trên Cloudinary.
