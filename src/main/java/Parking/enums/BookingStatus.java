package Parking.enums;

/**
 * Tập hợp các trạng thái của một Yêu cầu Đặt chỗ trước (Booking).
 */
public enum BookingStatus {
    PENDING,    // Đang chờ xử lý (Khách vừa bấm đặt)
    CONFIRMED,  // Đã xác nhận (Bãi đỗ đã giữ chỗ thành công)
    COMPLETED,  // Đã hoàn thành (Khách đã đến bãi đỗ và quẹt thẻ thành công)
    CANCELLED,  // Đã hủy (Do khách tự hủy hoặc nhân viên hủy)
    EXPIRED     // Hết hạn (Khách đặt nhưng không đến trong khoảng thời gian cho phép)
}
