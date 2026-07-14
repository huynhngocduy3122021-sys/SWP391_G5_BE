package Parking.enums;

/**
 * Trạng thái của một giao dịch thanh toán (Ví dụ: Thanh toán vé lượt, vé tháng).
 */
public enum PaymentStatus {
    PENDING,    // Đang chờ thanh toán (Khách vừa tạo hóa đơn nhưng chưa trả tiền)
    PAID,       // Đã thanh toán thành công
    FAILED,     // Thanh toán thất bại (Lỗi thẻ, không đủ tiền, rớt mạng lúc gọi VNPay)
    CANCELLED   // Giao dịch bị hủy bỏ
}
