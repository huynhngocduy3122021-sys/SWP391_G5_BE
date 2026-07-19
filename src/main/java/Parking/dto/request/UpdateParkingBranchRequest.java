package Parking.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateParkingBranchRequest {

    
    @Size(max = 255)
    private String branchName;

    
    @Size(max = 500)
    private String address;

    @Size(max = 20)
    private String phoneNumber;

    @Size(max = 500)
    private String description;
}
