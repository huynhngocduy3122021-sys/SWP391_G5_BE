package Parking.Model;


import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingCardType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Lớp Entity biểu diễn bảng "parking_card" trong cơ sở dữ liệu.
 * Đây là thực thể quản lý thông tin về thẻ giữ xe (thẻ cứng/thẻ từ) của bãi đỗ.
 */
@Entity
@Setter
@Getter
@Table(
    name = "parking_card", // Tên bảng trong database
    uniqueConstraints = {
        // Ràng buộc duy nhất (Unique Constraint) trên cột "card_code" - mã thẻ không được trùng
        @UniqueConstraint(
            name = "uk_parking_card_code",
            columnNames = "card_code"
        )
    }
)
public class ParkingCard {
    
    // Khóa chính của bảng, ID sẽ tự động tăng (IDENTITY)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parking_card_id")
    private Long parkingCardId;

    // Mã thẻ giữ xe (được in trên thẻ cứng hoặc chip từ). 
    // Bắt buộc phải có (nullable = false) và không được trùng lặp (unique = true)
    @Column(name = "card_code", nullable = false,unique = true)
    private String cardCode;

    // Trạng thái của thẻ (Ví dụ: AVAILABLE - có sẵn, IN_USE - đang sử dụng, LOST - bị mất)
    // Lưu vào database dưới dạng chữ (chuỗi String) thay vì số nguyên
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ParkingCardStatus status = ParkingCardStatus.AVAILABLE;

    // Loại thẻ (Ví dụ: REGULAR - thẻ vé lượt, MONTHLY - thẻ vé tháng)
    // Lưu vào database dưới dạng chuỗi (String) và không được để trống
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ParkingCardType type = ParkingCardType.REGULAR;

    // Mối quan hệ Nhiều-Một (Many-to-One): Nhiều thẻ có thể thuộc về một Chi nhánh bãi đỗ (ParkingBranch)
    // fetch = FetchType.LAZY: Tối ưu hiệu suất, chỉ query dữ liệu của chi nhánh khi thực sự gọi tới nhánh đó (chứ không query sẵn)
    // optional = false: Thẻ giữ xe bắt buộc phải thuộc về một chi nhánh (không được phép null)
    @ManyToOne(fetch = FetchType.LAZY , optional = false)
    @JoinColumn(name = "parking_branch_id") // Tên cột khóa ngoại trong bảng parking_card
    private ParkingBranch parkingBranch;


}
