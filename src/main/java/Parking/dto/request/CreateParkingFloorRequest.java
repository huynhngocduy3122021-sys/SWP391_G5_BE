package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateParkingFloorRequest {

    @NotBlank(message = "Floor name is required")
    @Size(max = 100)
    private String floorName;

    @NotNull(message = "Floor number is required")
    private Integer floorNumber;

    @Size(max = 255)
    private String description;

    @NotNull(message = "Parking branch id is required")
    private Long parkingBranchId;
}