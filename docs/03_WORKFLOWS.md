# 🔄 Quy trình Nghiệp vụ (Core Workflows)

Hệ thống có 3 quy trình cốt lõi chính: **Check-in (Xe vào)**, **Check-out & Tính phí (Xe ra)**, và **Tích hợp Thanh toán điện tử (VNPay)**.

---

## 📥 1. Quy trình Check-in (Xe vào)

Quy trình xe đi vào bãi đỗ và quét thẻ thành viên/thẻ lượt:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Nhân viên cổng vào
    participant Service as ParkingSessionService
    participant DB as SQL Server
    participant Cloudinary as Cloudinary API

    Client->>Service: Gửi yêu cầu Check-in (Biển số xe, Mã thẻ, Chi nhánh)
    Note over Service: 1. Chuẩn hóa biển số xe và mã thẻ
    Service->>DB: Kiểm tra thẻ có tồn tại & đang rảnh (AVAILABLE) không?
    DB-->>Service: Hợp lệ
    Service->>DB: Kiểm tra biển số xe này có đang đỗ trong bãi không? (Tránh trùng)
    DB-->>Service: Hợp lệ
    Service->>DB: Kiểm tra sức chứa bãi đỗ (Capacity) còn chỗ trống không?
    DB-->>Service: Hợp lệ
    opt Tải ảnh lên Cloudinary
        Client->>Service: Gửi kèm ảnh biển số/phương tiện
        Service->>Cloudinary: Đẩy ảnh lên đám mây Cloudinary
        Cloudinary-->>Service: Trả về URL ảnh
    end
    Service->>DB: Tạo mới ParkingSession (trạng thái ACTIVE)
    Service->>DB: Cập nhật thẻ sang trạng thái IN_USE
    DB-->>Service: Lưu thành công
    Service-->>Client: Trả về kết quả Check-in thành công (thông tin Session)
```

---

## 📤 2. Quy trình Check-out & Tính phí (Xe ra)

Quy trình tính toán thời gian, áp dụng chính sách giá và thực hiện thanh toán khi xe ra bãi:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Nhân viên cổng ra
    participant PS as ParkingSessionService
    participant PayS as PaymentService
    participant DB as SQL Server

    Client->>PS: Gửi yêu cầu Check-out (Mã thẻ, Biển số xe đối chiếu)
    PS->>DB: Tìm phiên gửi xe (ACTIVE) theo mã thẻ
    DB-->>PS: Trả về thông tin phiên gửi xe
    Note over PS: Đối chiếu biển số lúc vào với biển số lúc ra
    alt Biển số khớp
        PS->>PayS: Chuyển giao cho PaymentService xử lý thanh toán
        Note over PayS: Gọi hàm caculateParkingFee() để tính toán số tiền
        Note over PayS: Đọc chính sách giá (PricePolicy) áp dụng cho loại xe
        Note over PayS: Tính tổng thời gian đỗ xe = CheckOutTime - CheckInTime
        Note over PayS: Áp dụng phụ thu nếu vượt quá số giờ cơ bản (Làm tròn lên bằng Math.ceil)
        Note over PayS: Đảm bảo mức phí tối thiểu luôn là 10,000 VND
        PayS->>DB: Tạo bản ghi Payment (trạng thái PENDING nếu là VNPay, hoặc PAID nếu là Tiền mặt)
        DB-->>PayS: Trả về thông tin thanh toán
        PayS-->>PS: Trả về kết quả thanh toán/URL thanh toán
        PS-->>Client: Phản hồi thông tin thanh toán (Kèm URL nếu là VNPay)
    else Biển số không khớp
        PS-->>Client: Báo lỗi "Biển số xe lúc ra không khớp lúc vào!"
    end
```

---

## 💳 3. Tích hợp Thanh toán VNPay Sandbox

Khi khách chọn phương thức thanh toán là **VNPay**, hệ thống tạo URL thanh toán gửi về Client để khách quét mã hoặc nhập thẻ:

```mermaid
sequenceDiagram
    autonumber
    actor Guest as Khách hàng
    participant Client as Frontend / Mobile App
    participant BE as Spring Boot (Backend)
    participant VNPay as Cổng thanh toán VNPay Sandbox

    Guest->>Client: Xác nhận thanh toán qua VNPay
    Client->>BE: Gọi API Check-out phương thức VNPAY
    Note over BE: Gọi VnPayService để sinh URL thanh toán (Chữ ký SHA512)
    BE-->>Client: Trả về URL thanh toán VNPay
    Client->>Guest: Chuyển hướng trình duyệt sang trang thanh toán VNPay
    Guest->>VNPay: Thực hiện chuyển khoản/nhập thông tin thẻ
    VNPay->>VNPay: Xử lý giao dịch thành công
    
    par Luồng Redirect (Return URL)
        VNPay->>Client: Trình duyệt chuyển hướng về trang Return của ứng dụng (Kèm mã kết quả)
        Client->>BE: Gọi API verify kết quả giao dịch
        BE->>BE: Kiểm tra chữ ký bảo mật từ VNPay
        BE-->>Client: Xác nhận kết quả thanh toán thành công
        Client-->>Guest: Hiển thị màn hình "Thanh toán thành công!"
    and Luồng IPN (Background Call)
        Note over VNPay: Đảm bảo giao dịch được cập nhật kể cả khi khách tắt trình duyệt
        VNPay->>BE: Gọi ngầm vào API /api/payments/vnpay-ipn (thông qua Ngrok)
        BE->>BE: Kiểm tra chữ ký, cập nhật trạng thái Payment = PAID, Session = COMPLETED
        BE-->>VNPay: Phản hồi trạng thái nhận IPN thành công (Mã 00)
    end
```

---

## 📷 4. Lưu trữ ảnh với Cloudinary

Hệ thống chụp và lưu trữ hình ảnh của xe lúc vào/ra để phục vụ mục đích kiểm tra an ninh và đối chiếu thủ công khi cần thiết. 
* Toàn bộ mã nguồn xử lý tích hợp nằm tại file [VehicleImageService.java](file:///D:/Ki7/SWP391/Index/Index/src/main/java/Parking/Service/VehicleImageService.java).
* Các bức ảnh tải lên dạng `MultipartFile` sẽ được truyền lên server của Cloudinary qua API của hãng, sau đó đường dẫn hình ảnh (`url`) sẽ được lưu trực tiếp vào bảng `vehicle_image` kết nối với `parking_session_id`.
