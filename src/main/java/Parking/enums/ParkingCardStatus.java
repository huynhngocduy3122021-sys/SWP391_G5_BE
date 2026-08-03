package Parking.enums;

/**
 * Trạng thái của thẻ giữ xe vật lý (Thẻ từ RFID/NFC).
 */
public enum ParkingCardStatus {
    AVAILABLE,  // Thẻ trống, đang cất trong tủ, sẵn sàng cấp cho xe mới vào
    IN_USE,     // Thẻ đang được sử dụng (Có một xe đang giữ thẻ này trong bãi)
    LOST,       // Thẻ đã bị khách báo mất (Cần bị khóa để tránh kẻ gian nhặt được đem đi lấy xe)
    DISABLED    // Thẻ bị vô hiệu hóa (Bị hỏng chip, gãy, hỏng vật lý)
}
