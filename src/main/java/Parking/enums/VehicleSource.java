package Parking.enums;

/**
 * Nguồn gốc (Phân loại) phương tiện trong bãi đỗ.
 */
public enum VehicleSource {
    REGISTER,   // Phương tiện đã được đăng ký trên hệ thống (Của khách hàng có tài khoản)
    GUEST       // Phương tiện vãng lai (Của khách hàng không có tài khoản, hệ thống tự động ghi nhận biển số khi vào bãi)
}
