package Parking.enums;

/**
 * Các hành động (nhật ký) khi thao tác với một báo cáo sự cố.
 * Dùng để lưu vết (history) xem ai đã làm gì với sự cố này.
 */
public enum IncidentLogAction {
    CREATE,         // Tạo mới sự cố
    ASSIGN,         // Phân công cho một nhân viên xử lý
    UPDATE_STATUS,  // Cập nhật trạng thái sự cố
    UPLOAD_IMAGE,   // Tải hình ảnh hiện trường lên
    RESOLVE,        // Đánh dấu đã giải quyết xong
    CANCEL,         // Hủy bỏ báo cáo sự cố (do báo nhầm)
    VERIFY          // Quản lý xác minh lại sự cố
}
