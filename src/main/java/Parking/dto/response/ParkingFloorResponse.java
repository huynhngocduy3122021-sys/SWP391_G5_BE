package Parking.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ParkingFloorResponse {

    private Long parkingFloorId;
    private String floorName;
    private Integer floorNumber;
    private String description;
    private boolean active;

    private Long parkingBranchId;
    private String parkingBranchName;
}