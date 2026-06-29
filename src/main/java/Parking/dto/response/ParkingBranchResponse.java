package Parking.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ParkingBranchResponse {

    private Long parkingBranchId;
    private String branchName;
    private String address;
    private String phoneNumber;
    private String description;
    private boolean active;
    private Integer totalCapacity;
    private Integer availableCapacity;
}