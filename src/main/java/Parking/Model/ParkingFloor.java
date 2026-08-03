package Parking.Model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity đại diện cho một Tầng đỗ xe (Ví dụ: Tầng hầm B1, Tầng hầm B2).
 * Nằm bên trong một Chi nhánh (ParkingBranch).
 */
@Entity
@Getter
@Setter
@Table(
    name = "parking_floor",
    uniqueConstraints = {
        // Ràng buộc: Trong cùng 1 chi nhánh, không được có 2 tầng trùng số tầng (Ví dụ không được có 2 tầng số 1)
        @UniqueConstraint(
            name = "uk_branch_floor_number",
            columnNames = {
                "parking_branch_id",
                "floor_number"
            }
        )
    }
)
public class ParkingFloor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parking_floor_id")
    private Long parkingFloorId; // Mã tầng

    @Column(
        name = "floor_name",
        nullable = false,
        columnDefinition = "NVARCHAR(100)"
    )
    private String floorName; // Tên tầng (Ví dụ: "Hầm B1")

    @Column(
        name = "floor_number",
        nullable = false
    )
    private Integer floorNumber; // Số thứ tự tầng (Ví dụ: -1, 1, 2)

    @Column(
        name = "description",
        columnDefinition = "NVARCHAR(255)"
    )
    private String description;

    @Column(
        name = "active",
        nullable = false
    )
    private boolean active = true; // Tầng có đang hoạt động không? (Có thể đóng cửa để bảo trì)

    // Tầng này thuộc về Chi nhánh nào?
    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "parking_branch_id",
        nullable = false
    )
    private ParkingBranch parkingBranch;

    // Một tầng có một khu vực đỗ xe (ParkingZone) 
    // (Thiết kế hiện tại 1 Tầng tương ứng với 1 Zone, nếu muốn mở rộng 1 tầng có nhiều Zone thì đổi thành @OneToMany)
    @OneToOne(
        mappedBy = "parkingFloor",
        cascade = CascadeType.ALL,
        fetch = FetchType.LAZY
    )
    private ParkingZone parkingZone;
}