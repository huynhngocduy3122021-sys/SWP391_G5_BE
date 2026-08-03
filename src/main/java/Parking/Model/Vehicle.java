package Parking.Model;

import Parking.enums.VehicleSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Entity (Bảng trong Database) dùng để lưu thông tin về Phương tiện (Xe) của khách hàng.
 */
@Entity
@Getter
@Setter
@Table(
    name = "vehicles",
    uniqueConstraints = {
        // Ràng buộc duy nhất: Một biển số xe chỉ được lưu 1 lần trong toàn hệ thống
        @UniqueConstraint(
            name = "uk_vehicle_license_plate",
            columnNames = "license_plate"
        )
    }
)
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY )
    @Column(name = "vehicle_id")
    private Long vehiclesId;

    @Column(name = "license_plate", nullable = false, unique = true , length = 20)
    private String licensePlate; // Biển số xe

    @Column(name = "vehicle_color" , columnDefinition = "NVARCHAR(255)")
    private String vehicleColor; // Màu xe

    @Column(name = "vehicle_brand",columnDefinition = "NVARCHAR(100)")
    private String vehicleBrand; // Thương hiệu/Hãng xe (Ví dụ: Honda, Toyota)

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_source",nullable = false)
    private VehicleSource vehicleSource = VehicleSource.GUEST; // Phân loại xem xe này của Khách vãng lai hay Khách đã có tài khoản (REGISTER)

    // Liên kết với chủ xe (Có thể NULL nếu đây là xe của khách vãng lai không có tài khoản)
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    // Phân loại xe (Ví dụ: Xe máy điện, Xe máy xăng, Ô tô 4 chỗ, Ô tô 7 chỗ)
    @ManyToOne
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false; // Xóa mềm (Soft delete)
}
