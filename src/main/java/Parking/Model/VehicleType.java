package Parking.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
/**
 * Entity đại diện cho Phân loại Phương tiện.
 * Ví dụ: Xe đạp, Xe máy số, Xe tay ga, Ô tô 4 chỗ, Ô tô 7 chỗ.
 */
@Entity
@Setter
@Getter
@Table(name= "vehicle_type")
public class VehicleType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_type_id")
    private Long vehicleTypeId; // Mã loại xe

    @Column(name = "type_name", columnDefinition = "NVARCHAR(255)", nullable = false)
    private String typeName; // Tên loại xe (Ví dụ: "Ô tô con")

    @Column(name = "description" , columnDefinition = "NVARCHAR(255)")
    private String description; // Mô tả thêm chi tiết
}
