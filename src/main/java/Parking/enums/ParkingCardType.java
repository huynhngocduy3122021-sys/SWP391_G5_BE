package Parking.enums;

/**
 * Phân loại thẻ giữ xe (Dựa vào mục đích sử dụng).
 */
public enum ParkingCardType {
    REGULAR,    // Thẻ thường (Dùng cho khách vãng lai gửi vé lượt)
    MONTHLY,    // Thẻ tháng (Dùng cho khách hàng đăng ký gửi cố định)
    VIP,        // Thẻ VIP (Khách hàng đặc biệt, có vị trí đỗ riêng)
    EMPLOYEE    // Thẻ nhân viên (Dùng cho nhân viên nội bộ của bãi đỗ / tòa nhà)
}
