package Parking.enums;

/**
 * Trạng thái của một phiên đỗ xe (Parking Session).
 * Theo dõi quá trình từ lúc xe vào cho đến lúc xe ra khỏi bãi.
 */
public enum ParkingSessionStatus {
    ACTIVE,     // Đang hoạt động (Xe đã quẹt thẻ vào và vẫn đang nằm trong bãi)
    COMPLETED,  // Đã hoàn thành (Xe đã quẹt thẻ ra và thanh toán xong xuôi)
    CANCELLED   // Đã hủy (Phiên gửi xe bị hủy do lỗi quẹt nhầm hoặc thao tác của quản lý)
}
