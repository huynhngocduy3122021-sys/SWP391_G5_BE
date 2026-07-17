# Báo cáo duy chứng Sử dụng AI trong Dự án (Parking System)

**Dự án:** Quản lý bãi đỗ xe (SWP391)
**Công cụ AI sử dụng:** Antigravity AI (Gemini)

Dưới đây là danh sách các module và tính năng đã được tinh chỉnh, tối ưu hóa logic bằng AI dựa trên lịch sử phiên bản (Git Commits) trên máy của bạn.

---

## 1. Tính năng Check-In và Check-Out (Parking Session & Booking)
**Commit:** `7700319` - *feat: Enhance parking session and booking features with optional time parameter*

**Mô tả các thay đổi do AI hỗ trợ thực hiện:**
AI đã tham gia vào việc thay đổi và tối ưu hóa logic điểm danh (Check-in/Check-out) cho bãi đỗ xe, đặc biệt là xử lý tham số thời gian tùy chọn để tính toán và lưu trữ chính xác thời gian ra/vào của phương tiện.

**Các file đã được AI can thiệp chỉnh sửa:**
- `ParkingSessionService.java`
- `ParkingSessionController.java`
- `GuestCheckInRequest.java`
- `GuestCheckOutRequest.java`
- `BookingService.java`

**Đoạn code logic tiêu biểu (AI đề xuất):**
Việc bổ sung điều kiện tham số thời gian (optional time parameter) giúp hệ thống tính phí linh hoạt hơn khi khách ra/vào bãi.

---

## 2. Tích hợp thanh toán VNPay và Luồng trả về (VNPay Return Flow)
**Commits:** `b632ac2`, `63a4020`, `e28e9c3` - *feat: Update VNPay return flow for monthly ticket and staff checkout*

**Mô tả các thay đổi do AI hỗ trợ thực hiện:**
AI đã hỗ trợ xây dựng luồng thanh toán VNPay, sửa các lỗi xung đột (merge conflicts) và cấu hình lại URL frontend khi thanh toán thành công/thất bại.

**Các file đã được AI can thiệp chỉnh sửa:**
- `VnPayService.java` & `VnPayConfig.java`
- `PaymentService.java` & `PaymentController.java`
- `docs/15_VNPAY_RETURN_FLOW_FIX.md`
- `docs/16_MONTHLY_TICKET_PAYMENT_SYNC.md`

---

## 3. Quản lý Vé tháng (Monthly Ticket)
**Commits:** `edd17c6`, `59abeb1`, `f5a2e1e` - *feat: Implement monthly ticket payment functionality*

**Mô tả các thay đổi do AI hỗ trợ thực hiện:**
AI đã giúp khởi tạo và liên kết dữ liệu giữa các Repository và Service để quản lý việc mua, gia hạn vé tháng và tự động tính toán giá dựa trên `PricePolicy`.

**Các file đã được AI can thiệp chỉnh sửa:**
- `MonthlyTicketService.java` & `MonthlyTicketRequestController.java`
- `MonthlyTicket.java`, `MonthlyTicketRequest.java`
- `MonthlyTicketRepository.java`, `PricePolicyRepository.java`

---

### Xác nhận
> *Báo cáo này được tự động trích xuất từ lịch sử thao tác Git nội bộ của dự án kết hợp với nhật ký phân tích của AI (Antigravity).*
