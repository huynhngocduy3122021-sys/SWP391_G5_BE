package Parking.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity đại diện cho Khu vực đỗ xe (Ví dụ: Khu vực xe máy, Khu vực ô tô).
 */
@Entity
@Getter
@Setter
@Table(name = "parking_zone")
public class ParkingZone {
    
     @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parking_zone_id")
    private Long parkingZoneId; // Mã khu vực

    @Column(name = "zone_name", nullable = false)
    private String zoneName; // Tên khu vực (Ví dụ: "Khu A - Ô tô", "Khu B - Xe máy")

    @Column(name = "capacity", nullable = false)
    private Integer capacity; // Sức chứa tối đa (Tổng số chỗ đỗ trong khu này)

    @Column(name = "active", nullable = false)
    private boolean active = true;

    // Khu vực này nằm ở Tầng nào?
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "parking_floor_id",
        nullable = false,
        unique = true
    )
    private ParkingFloor parkingFloor;

    // Khu vực này dành riêng cho Loại xe nào đỗ? (Chỉ xe máy mới được vào khu xe máy)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "vehicle_type_id",
        nullable = false
    )
    private VehicleType vehicleType;

}
