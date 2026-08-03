package Parking.enums;


/**
 * Quyền hạn (Role) của người dùng trong hệ thống.
 * Spring Security sẽ dùng các chữ này để quyết định xem ai được gọi API nào.
 */
public enum UserRole {
   ADMIN,    // Quản trị viên hệ thống (Toàn quyền)
   STAFF,    // Nhân viên soát vé / Nhân viên trực bãi
   MANAGER,  // Quản lý bãi đỗ xe (Được quyền duyệt vé tháng, xem báo cáo)
   USER      // Khách hàng bình thường (Chỉ được xem lịch sử gửi xe của bản thân)
}
