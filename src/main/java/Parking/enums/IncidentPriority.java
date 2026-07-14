package Parking.enums;

/**
 * Mức độ nghiêm trọng / Mức độ ưu tiên của sự cố.
 */
public enum IncidentPriority {
    LOW,        // Thấp (Ví dụ: Thẻ bị xước nhẹ nhưng vẫn đọc được)
    MEDIUM,     // Trung bình (Ví dụ: Barrier mở chậm)
    HIGH,       // Cao (Ví dụ: Mất thẻ giữ xe, hệ thống tính sai tiền)
    CRITICAL    // Nghiêm trọng (Ví dụ: Báo mất xe, cháy nổ, hỏng toàn bộ hệ thống camera)
}
