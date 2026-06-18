package Parking.dto.response;

import Parking.enums.VehicleSource;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class VehicleResponse {
    private Long vehicleId;
    private String licensePlate;
    private String vehicleColor;
    private String vehicleBrand;
    private VehicleSource vehicleSource;
    private Long userId;
    private String userFullName;
    private Long vehicleTypeId;
    private String vehicleTypeName;
    private boolean deleted;
}
