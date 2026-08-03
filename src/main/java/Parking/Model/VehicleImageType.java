package Parking.Model;

/**
 * Phân loại hình ảnh xe chụp tại bãi.
 */
public enum VehicleImageType {
     CHECK_IN,      // Chụp mặt người và biển số lúc vào
    CHECK_OUT,      // Chụp mặt người và biển số lúc ra
    LICENSE_PLATE   // Ảnh crop cận cảnh chỉ có biển số xe (Dành cho AI nhận diện)
}
