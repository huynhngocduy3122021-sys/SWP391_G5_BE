package Parking.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParkingCapacityResponse {

    private Long parkingBranchId;
    private String branchName;
    private Integer totalCapacity;
    private Integer occupiedCapacity;
    private Integer reservedCapacity;
    private Integer availableCapacity;
}
