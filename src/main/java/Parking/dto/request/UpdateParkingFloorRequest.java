
package Parking.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateParkingFloorRequest {

    
    @Size(max = 100)
    private String floorName;

    
    
    private Integer floorNumber;

    @Size(max = 255)
    private String description;

    
    private Long parkingBranchId;
}
