package Parking.enums;

public enum IncidentType {
    LOST_CARD,         // Mất thẻ xe
    TECHNICAL_ERROR,   // Lỗi kỹ thuật (barrier, camera, đầu đọc thẻ...)
    PAYMENT_ERROR,     // Lỗi thanh toán trực tuyến/tiền mặt
    VEHICLE_DAMAGE,    // Xe bị va chạm, hư hại
    SECURITY_INCIDENT, // Trộm cắp, tranh chấp, mất an ninh
    POWER_OUTAGE,      // Mất điện toàn cục/một phần bãi xe
    BARRIER_ERROR,     // Barrier không mở
    OTHER              // Sự cố khác
}
