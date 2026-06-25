# 💳 Tài liệu các hàm thanh toán (Payment & VNPay Services)

Tài liệu này giải thích chi tiết các hàm trong hai dịch vụ cốt lõi: [PaymentService.java](file:///D:/Ki7/SWP391/Index/Index/src/main/java/Parking/Service/PaymentService.java) và [VnPayService.java](file:///D:/Ki7/SWP391/Index/Index/src/main/java/Parking/Service/VnPayService.java).

---

## 🛠️ Phần I: Dịch vụ Thanh toán (PaymentService)

### 1. `processCheckOutPayment(ParkingSession session, PaymentMethod method, String clientIp)`
* **Chức năng:** Khởi tạo giao dịch thanh toán cho phiên gửi xe, phân loại phương thức Tiền mặt hoặc VNPay.
* **Tại sao cần làm vậy?** 
  Sau khi đối chiếu biển số xe thành công, hệ thống cần khóa phiên, tính phí và chốt hóa đơn. Nếu khách chọn trả tiền mặt, hệ thống đóng phiên ngay. Nếu chọn VNPay, hệ thống giữ trạng thái chờ thanh toán (PENDING) và sinh liên kết quét mã QR.
* **Quy trình hoạt động:**
  1. Kiểm tra phiên đã thanh toán trước đó chưa (tránh trả tiền trùng lặp). Nếu có giao dịch `PENDING` cũ chưa hoàn tất, xóa đi để tạo giao dịch mới.
  2. Lấy chính sách giá (`PricePolicy`) kích hoạt gần nhất cho loại xe của phiên đó.
  3. Gọi hàm `caculateParkingFee` để tính tổng chi phí.
  4. Tạo thực thể `Payment` mới kèm mã tham chiếu duy nhất `transactionRef`.
  5. **Nếu là tiền mặt (CASH):**
     * Đặt trạng thái giao dịch là `PAID`.
     * Chốt thời gian ra (`checkOutTime`), cập nhật trạng thái phiên thành `COMPLETED`.
     * Thu hồi và chuyển trạng thái thẻ gửi xe vật lý về `AVAILABLE`.
     * Lưu thông tin và kết thúc.
  6. **Nếu là VNPay (VNPAY):**
     * Đặt trạng thái giao dịch là `PENDING` (đang chờ).
     * Đặt thời gian hết hạn thanh toán là **15 phút** kể từ thời điểm hiện tại (tránh giữ phiên vô hạn).
     * Gọi `VnPayService.createPaymentUrl` để sinh đường dẫn thanh toán.

---

### 2. `caculateParkingFee(LocalDateTime checkIn, LocalDateTime checkOut, PricePolicy policy)`
* **Chức năng:** Tính toán chi phí gửi xe dựa trên cấu hình giá và thời lượng đỗ thực tế.
* **Quy trình hoạt động:**
  1. Tính khoảng cách phút gửi: `Duration.between(checkIn, checkOut).toMinutes()`.
  2. Nếu số phút nhỏ hơn thời gian tối thiểu (`baseDurationMinutes`), chỉ tính phí cơ bản (`basePrice`).
  3. Nếu số phút vượt quá thời gian tối thiểu:
     * Tính số phút phát sinh dư thừa: `extraMinutes = totalMinutes - baseDurationMinutes`.
     * Tính số giờ phát sinh: `long extraHours = (long) Math.ceil(extraMinutes / 60.0);` (Làm tròn lên bất kỳ số phút dư nào để tính đủ theo giờ).
     * Số tiền phát sinh = `extraHourPrice * extraHours`.
     * Tổng số tiền = `basePrice + số tiền phát sinh`.
  4. Kiểm tra điều kiện chặn dưới: Đảm bảo số tiền cuối cùng không dưới **10,000 VND** (đáp ứng điều kiện thanh toán của VNPay Sandbox).

---

### 3. `handleVnPayCallback(Map<String, String> params)`
* **Chức năng:** Nhận kết quả phản hồi từ trang thanh toán VNPay khi trình duyệt của người dùng tự động chuyển hướng quay lại (Redirect Return).
* **Quy trình hoạt động:**
  1. Xác thực chữ ký dữ liệu từ VNPay (`verifySignature`) để đảm bảo dữ liệu không bị sửa đổi trên đường truyền.
  2. Tìm kiếm thông tin thanh toán dựa trên mã `vnp_TxnRef`.
  3. Nếu mã phản hồi `vnp_ResponseCode = "00"` (Thành công):
     * Cập nhật trạng thái thanh toán thành `PAID` và ghi nhận thời gian trả tiền.
     * Cập nhật phiên gửi xe thành `COMPLETED` và giải phóng thẻ xe về trạng thái `AVAILABLE`.
  4. Nếu thất bại, cập nhật trạng thái thanh toán thành `FAILED`.

---

### 4. `handleVnPayIpn(Map<String, String> params)`
* **Chức năng:** Cổng thu nhận dữ liệu giao dịch tự động từ VNPay gọi trực tiếp tới Backend (Instant Payment Notification).
* **Tại sao cần hàm này?**
  Khi người dùng thanh toán xong, nếu họ lỡ tay tắt trình duyệt, tắt mạng hoặc gặp sự cố chuyển hướng làm mất liên kết quay lại trang Web, luồng Callback sẽ không chạy. VNPay sẽ tự động gọi ngầm API IPN này nhiều lần để đảm bảo Backend cập nhật đúng trạng thái hóa đơn.
* **Kiểm tra an toàn (Anti-fraud):**
  * Xác thực chữ ký Hash bảo mật.
  * Kiểm tra xem mã merchant (`vnp_TmnCode`) có khớp với hệ thống của mình không.
  * Đối chiếu số tiền giao dịch (`vnp_Amount`) từ VNPay gửi về với số tiền lưu ở database. Nếu sai số tiền, báo lỗi phá hoại giao dịch.
  * Đảm bảo giao dịch chưa được xác nhận hoàn thành trước đó (tránh xử lý trùng lặp).

---

## 🌐 Phần II: Dịch vụ tích hợp VNPay API (VnPayService)

### 1. `createPaymentUrl(Payment payment, String clientIp)`
* **Chức năng:** Tạo liên kết thanh toán chứa mã QR và thông tin hóa đơn gửi sang cổng VNPay.
* **Tại sao cần?** Tạo ra chuỗi truy vấn chuẩn hóa (Query String) đã được mã hóa và tạo chữ ký số để bảo vệ thông tin thanh toán.
* **Quy trình hoạt động:**
  * Thu thập dữ liệu: Mã Merchant, số tiền (nhân 100 lần theo quy định VNPay), mã tham chiếu hóa đơn, mô tả giao dịch, IP máy khách, đường link nhận phản hồi, thời gian tạo và hết hạn hóa đơn.
  * Sắp xếp các khóa tham số theo thứ tự bảng chữ cái (Bắt buộc theo chuẩn VNPay).
  * Mã hóa chuỗi truy vấn bằng thuật toán **HMAC-SHA512** với khóa bí mật (`HashSecret`) của hệ thống để tạo chuỗi chữ ký số (`vnp_SecureHash`).
  * Trả về chuỗi URL đầy đủ.

### 2. `verifySignature(Map<String, String> originalParams)`
* **Chức năng:** Kiểm tra tính toàn vẹn của chữ ký số nhận về từ VNPay Callback/IPN.
* **Tại sao cần?** Ngăn chặn hacker gửi yêu cầu giả mạo thông báo thanh toán thành công để trốn phí gửi xe.
* **Cách thực hiện:** Lọc bỏ các tham số không tham gia ký (`vnp_SecureHash`), sắp xếp các trường còn lại, băm lại bằng khóa bí mật cục bộ và đối chiếu với chữ ký nhận được.

### 3. `normalizeIpAddress(String clientIp)`
* **Chức năng:** Chuẩn hóa địa chỉ IP của Client.
* **Tại sao cần?** Trong môi trường cục bộ (localhost), IP có thể nhận dạng là IPv6 (`::1`). VNPay yêu cầu IP hợp lệ dạng IPv4, do đó hàm này tự động chuyển đổi các dải IP cục bộ hoặc IP từ proxy thành định dạng IP IPv4 chuẩn (ví dụ: `127.0.0.1`).
