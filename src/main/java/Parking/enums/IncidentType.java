package Parking.enums;

/**
 * Phân loại các sự cố có thể xảy ra trong bãi đỗ xe.
 */
public enum IncidentType {
    LOST_CARD,         // Mất thẻ xe (Khách làm rơi, mất)
    TECHNICAL_ERROR,   // Lỗi kỹ thuật (Hỏng barrier, hỏng camera, đầu đọc thẻ không nhận)
    PAYMENT_ERROR,     // Lỗi thanh toán (Chuyển khoản bị treo, máy POS lỗi)
    VEHICLE_DAMAGE,    // Hư hại phương tiện (Xe bị quẹt, vỡ gương, xước sơn trong bãi)
    SECURITY_INCIDENT, // Sự cố an ninh (Nghi ngờ trộm cắp, tranh chấp xe, gây rối)
    POWER_OUTAGE,      // Mất điện toàn cục hoặc cục bộ bãi xe
    BARRIER_ERROR,     // Barrier bị kẹt, không mở hoặc không đóng
    OTHER              // Các sự cố khác không nằm trong danh sách trên
}
