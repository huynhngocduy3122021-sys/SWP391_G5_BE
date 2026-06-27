# Tài liệu Phương pháp Triển khai Booking Backend (Đã hiệu chỉnh theo Review)

Tài liệu này trình bày chi tiết thiết kế hệ thống và phương pháp code cho tính năng đặt chỗ đỗ xe trước (Booking) ở phía Backend, tích hợp toàn bộ các góp ý từ tài liệu đánh giá [10_BOOKING_METHODOLOGY_REVIEW.md](file:///D:/Ki7/SWP391/Index/Index/docs/10_BOOKING_METHODOLOGY_REVIEW.md).

---

## 1. Thiết kế Mô hình Dữ liệu (Database Schema)

Để đảm bảo khả năng đối khớp chính xác, kiểm toán dòng tiền và quản lý trạng thái, mô hình dữ liệu sẽ được thiết kế như sau:

### 1.1. Thực thể `Booking` (Bảng `booking`)
Bảng này sẽ lưu trữ thông tin giữ chỗ của khách hàng:
* **`booking_id`** (Long, Primary Key): Khóa chính tự động tăng.
* **`user_id`** (Long, Foreign Key): Tham chiếu đến bảng `users` (Khách hàng đặt chỗ).
* **`parking_branch_id`** (Long, Foreign Key): Tham chiếu đến bảng `parking_branch` (Chi nhánh đặt chỗ).
* **`vehicle_id`** (Long, Foreign Key): Tham chiếu đến bảng `vehicles` (Xe thực hiện đặt chỗ).
* **`vehicle_type_id`** (Long, Foreign Key): Tham chiếu đến bảng `vehicle_type` (Loại xe, ví dụ: `CAR`, `ELECTRIC_CAR`).
* **`parking_session_id`** (Long, Foreign Key, Nullable): Tham chiếu đến bảng `parking_session` (Chỉ được gán khi xe đã vào bãi và booking được hoàn thành).
* **`expected_arrival_time`** (LocalDateTime): Thời gian khách hàng hẹn sẽ đến bãi đỗ xe.
* **`hold_until`** (LocalDateTime): Thời điểm hết hạn giữ chỗ (bằng `expected_arrival_time` cộng thêm thời gian ân hạn, mặc định là 30 phút).
* **`status`** (Enum/String): Trạng thái của booking. Định nghĩa qua enum `BookingStatus`:
  * `PENDING`: Đang chờ thanh toán cọc giữ chỗ (nếu có).
  * `CONFIRMED`: Đã đặt chỗ thành công và đang giữ chỗ trong bãi.
  * `COMPLETED`: Xe đã vào bãi đỗ thành công (đã liên kết với `ParkingSession`).
  * `CANCELLED`: Khách hàng hoặc hệ thống hủy đặt chỗ.
  * `EXPIRED`: Quá hạn giữ chỗ `hold_until` mà xe không vào bãi.
* **`deposit_amount`** (BigDecimal, Nullable): Số tiền đặt cọc (nếu áp dụng chính sách thu phí).
* **`created_at`** (LocalDateTime): Thời điểm tạo booking.
* **`updated_at`** (LocalDateTime): Thời điểm cập nhật booking gần nhất.
* **`cancelled_at`** (LocalDateTime, Nullable): Thời điểm hủy booking.
* **`completed_at`** (LocalDateTime, Nullable): Thời điểm hoàn thành check-in từ booking.
* **`expired_at`** (LocalDateTime, Nullable): Thời điểm hệ thống hủy tự động do quá hạn.

### 1.2. Chỉ mục cơ sở dữ liệu (Indexes) đề xuất
Để tối ưu tốc độ truy vấn khi scheduler quét hoặc kiểm tra sức chứa:
* Index trên `(user_id, status)` - Dùng khi hiển thị booking của user.
* Index trên `(parking_branch_id, vehicle_type_id, status, hold_until)` - Dùng khi tính toán slot khả dụng.
* Index trên `(vehicle_id, status)` - Dùng kiểm tra tính trùng lặp đặt chỗ của xe.

---

## 2. Quy tắc Nghiệp vụ & Xác thực (Business Rules & Validations)

### 2.1. Xác thực Loại Xe Hợp Lệ (Booking Policy)
* Hệ thống chỉ chấp nhận booking cho các loại xe có trường `typeName` là:
  * `CAR` (Ô tô)
  * `ELECTRIC_CAR` (Ô tô điện)
* Mọi yêu cầu booking với loại xe `MOTORBIKE` (Xe máy) hoặc `ELECTRIC_MOTORBIKE` (Xe máy điện) sẽ bị chặn ngay lập tức tại `BookingService` và ném ra `BookingException`.

### 2.2. Kiểm tra Biển Số & Ràng Buộc Đặt Chỗ
* **Bắt buộc biển số xe**: Khách hàng phải truyền biển số xe (`licensePlate`) khi tạo booking.
  * Nếu biển số xe chưa tồn tại trong bảng `vehicles`, hệ thống sẽ tự động tạo một thực thể `Vehicle` mới dưới quyền sở hữu của user đang đặt chỗ.
  * Nếu xe đã tồn tại nhưng thuộc sở hữu của user khác, hệ thống sẽ yêu cầu xác thực hoặc báo lỗi tùy quy trình nghiệp vụ.
* **Giới hạn Booking đồng thời**: Mỗi user hoặc mỗi phương tiện (`Vehicle`) chỉ được phép có tối đa **1** booking ở trạng thái `CONFIRMED` hoặc `PENDING` tại một thời điểm để tránh spam giữ chỗ ảo.

### 2.3. Ràng buộc về thời gian đặt trước
* Thời gian dự kiến đến (`expectedArrivalTime`) phải lớn hơn thời gian hiện tại ít nhất 15 phút và không vượt quá 7 ngày trong tương lai.
* Hệ thống tự động tính toán thời gian giải phóng chỗ đỗ: `holdUntil = expectedArrivalTime + 30 phút` (Grace Period).

---

## 3. Thuật toán Tính toán Chỗ trống & Chống Overbooking

Để tránh việc nhận quá số lượng xe có thể đỗ, việc tính toán số slot trống khả dụng cho một chi nhánh và một loại xe tại thời điểm dự kiến đến sẽ áp dụng công thức sau:

$$S_{\text{khả dụng}} = C_{\text{tổng}} - S_{\text{đang hoạt động}} - B_{\text{đã đặt chỗ}}$$

Trong đó:
1. **$C_{\text{tổng}}$ (Total Capacity)**: Tổng capacity của tất cả các `ParkingZone` đang hoạt động của chi nhánh tương thích với loại xe đó.
2. **$S_{\text{đang hoạt động}}$ (Active Sessions)**: Tổng số xe của loại xe đó đang đỗ thực tế trong bãi (số `ParkingSession` có trạng thái `ACTIVE`).
3. **$B_{\text{đã đặt chỗ}}$ (Confirmed Bookings)**: Số lượng booking có trạng thái `CONFIRMED` có thời gian giữ chỗ giao thoa với thời điểm khách hàng đặt chỗ.
   * Để đơn giản và an toàn, ta đếm các booking `CONFIRMED` có thời gian hết hạn giữ chỗ `holdUntil` lớn hơn thời điểm hiện tại và dự kiến đến của booking mới.

---

## 4. Giải pháp Chống Race Condition (Tranh chấp đồng thời)

Khi có hàng chục yêu cầu đặt chỗ gửi lên cùng một lúc cho 1 slot trống cuối cùng:
* Hệ thống sẽ sử dụng cơ chế **Pessimistic Locking (Khóa bi quan)** trên chi nhánh hoặc vùng đỗ.
* Trước khi tiến hành đếm slot trống và tạo booking mới, `BookingService` sẽ gọi một phương thức repository để lock chi nhánh đó lại:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM ParkingBranch b WHERE b.parkingBranchId = :id")
  Optional<ParkingBranch> findAndLockBranchById(@Param("id") Long id);
  ```
* Việc này đảm bảo các luồng (thread) khác yêu cầu đặt chỗ trên cùng một chi nhánh sẽ phải xếp hàng chờ cho đến khi giao dịch hiện tại hoàn thành (commit hoặc rollback), triệt tiêu hoàn toàn rủi ro overbooking.

---

## 5. Luồng Tích hợp Check-in & Scheduler Hủy Booking Quá Hạn

### 5.1. Luồng Check-in khi có đặt chỗ trước
Khi nhân viên tiến hành quét biển số xe để check-in cho khách:
1. Hệ thống tìm kiếm các bản ghi `Booking` có trạng thái `CONFIRMED` liên kết với `vehicle` mang biển số vừa quét, và thời gian hiện tại phải nằm trong khoảng:
   $$\text{expectedArrivalTime} - 15 \text{ phút} \le \text{currentTime} \le \text{holdUntil}$$
2. Nếu tìm thấy booking hợp lệ:
   * Tiến hành tạo phiên gửi xe `ParkingSession` mới như bình thường.
   * Chuyển trạng thái thẻ từ sang `IN_USE`.
   * Cập nhật bản ghi `Booking`: set trạng thái sang `COMPLETED`, ghi nhận `completed_at = LocalDateTime.now()`, và liên kết với `parking_session_id` vừa tạo.
3. Nếu không tìm thấy booking đặt trước phù hợp, hệ thống vẫn tiến hành check-in theo dạng khách vãng lai thông thường (nếu bãi xe còn chỗ cho xe vãng lai).

### 5.2. Scheduler tự động quét hủy giữ chỗ hết hạn (Cleanup Job)
* Sử dụng `@Scheduled(cron = "0 */5 * * * *")` để chạy định kỳ mỗi 5 phút.
* Cơ chế xử lý:
  1. Truy vấn danh sách các `Booking` có `status = 'CONFIRMED'` và `holdUntil < current_time`.
  2. Với mỗi booking quá hạn:
     * Cập nhật trạng thái thành `EXPIRED`.
     * Ghi nhận `expired_at = LocalDateTime.now()`.
     * Giải phóng slot ảo để hệ thống cập nhật lại số lượng chỗ trống chính xác.

---

## 6. Đề xuất Phân Quyền & Bảo Mật (Authorization)

* **Nhận diện User**: Đầu vào API tạo/xem booking sẽ không truyền `userId` trực tiếp trong Request Body. ID của người dùng sẽ được trích xuất an toàn từ JWT Token ở lớp Security Context của Spring.
* **Hủy đặt chỗ**: 
  * Khách hàng thông thường chỉ có quyền hủy (`CANCELLED`) các booking do chính mình tạo ra (kiểm tra `booking.user.userId == currentUserId`).
  * Admin hoặc Staff được quyền hủy mọi booking trên hệ thống để hỗ trợ khách hàng khi cần.

---

## 7. Cấu trúc Lớp Đề xuất trong Backend

Triển khai tính năng này theo cấu trúc gói hiện có của dự án:

* **Model**:
  * `Parking.Model.Booking`
  * `Parking.enums.BookingStatus`
* **Repository**:
  * `Parking.Repository.BookingRepository` (Cung cấp các hàm tìm kiếm theo trạng thái, user, branch, và các câu lệnh lock).
* **Service**:
  * `Parking.Service.BookingService` (Chứa logic kiểm tra loại xe, kiểm tra chỗ trống khả dụng, xử lý transactional lock, hủy/nhận xe).
* **Controller**:
  * `Parking.Controller.BookingController` (Expose các REST endpoint bảo mật).
* **DTO**:
  * `Parking.dto.request.CreateBookingRequest`
  * `Parking.dto.response.BookingResponse`
* **Exception**:
  * `Parking.exception.exceptions.BookingException` (Exception tùy biến xử lý lỗi liên quan đến booking).
