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

@Entity
@Getter
@Setter
@Table(
    name = "vehicles",
    uniqueConstraints = {
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
    private String licensePlate;

    @Column(name = "vehicle_color" , columnDefinition = "NVARCHAR(255)")
    private String vehicleColor;

    @Column(name = "vehicle_brand",columnDefinition = "NVARCHAR(100)")
    private String vehicleBrand;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_source",nullable = false)
    private VehicleSource vehicleSource = VehicleSource.GUEST;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @ManyToOne
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;
}
