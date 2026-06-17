package Parking.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ParkingZoneResponse {

    private Long parkingZoneId;
    private String zoneName;
    private Integer capacity;
    private boolean active;

    private Long parkingFloorId;
    private String parkingFloorName;

    private Long parkingBranchId;
    private String parkingBranchName;

    private Long vehicleTypeId;
    private String vehicleTypeName;
}