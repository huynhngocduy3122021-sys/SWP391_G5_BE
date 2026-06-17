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

@Entity
@Getter
@Setter
@Table(name = "parking_zone")
public class ParkingZone {
    
     @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parking_zone_id")
    private Long parkingZoneId;

    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "parking_floor_id",
        nullable = false,
        unique = true
    )
    private ParkingFloor parkingFloor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "vehicle_type_id",
        nullable = false
    )
    private VehicleType vehicleType;

}
