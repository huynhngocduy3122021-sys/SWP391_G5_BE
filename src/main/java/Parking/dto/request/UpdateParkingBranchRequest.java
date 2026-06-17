package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateParkingBranchRequest {

    @NotBlank(message = "Branch name is required")
    @Size(max = 255)
    private String branchName;

    @NotBlank(message = "Address is required")
    @Size(max = 500)
    private String address;

    @Size(max = 20)
    private String phoneNumber;

    @Size(max = 500)
    private String description;
}