package Parking.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
@Entity
@Setter
@Getter
@Table(name= "vehicle_type")
public class VehicleType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_type_id")
    private Long vehicleTypeId;

    @Column(name = "type_name", columnDefinition = "NVARCHAR(255)", nullable = false)
    private String typeName;

    @Column(name = "description" , columnDefinition = "NVARCHAR(255)")
    private String description;
}
