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

@Entity
@Getter
@Setter
@Table(
    name = "parking_floor",
    uniqueConstraints = {
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
    private Long parkingFloorId;

    @Column(
        name = "floor_name",
        nullable = false,
        columnDefinition = "NVARCHAR(100)"
    )
    private String floorName;

    @Column(
        name = "floor_number",
        nullable = false
    )
    private Integer floorNumber;

    @Column(
        name = "description",
        columnDefinition = "NVARCHAR(255)"
    )
    private String description;

    @Column(
        name = "active",
        nullable = false
    )
    private boolean active = true;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "parking_branch_id",
        nullable = false
    )
    private ParkingBranch parkingBranch;

    @OneToOne(
        mappedBy = "parkingFloor",
        cascade = CascadeType.ALL,
        fetch = FetchType.LAZY
    )
    private ParkingZone parkingZone;
}