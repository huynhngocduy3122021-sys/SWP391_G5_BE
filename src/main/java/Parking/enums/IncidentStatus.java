package Parking.enums;

/**
 * Trạng thái hiện tại của một sự cố.
 */
public enum IncidentStatus {
    PENDING,                    // Đang chờ tiếp nhận
    IN_PROGRESS,                // Nhân viên đang tiến hành xử lý/khắc phục
    WAITING_PAYMENT,            // Chờ khách hàng đền bù (Ví dụ: đền tiền mất thẻ)
    WAITING_MANAGER_APPROVAL,   // Chờ quản lý cấp cao duyệt (Ví dụ: bồi thường mất xe)
    RESOLVED,                   // Đã giải quyết xong xuôi
    CANCELLED                   // Đã hủy bỏ
}
