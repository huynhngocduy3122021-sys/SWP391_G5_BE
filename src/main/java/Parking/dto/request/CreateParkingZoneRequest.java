package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class CreateParkingZoneRequest {
    @NotBlank(message = "Zone name is required")
    @Size(max = 100, message = "Zone name must not exceed 100 characters")
    private String zoneName;

    @NotNull(message = "Max capacity is required")
    @Positive(message = "Max capacity must be greater than 0")
    private Integer maxCapacity;

    @NotNull(message = "Vehicle type id is required")
    @Positive(message = "Vehicle type id must be greater than 0")
    private Long vehicleTypeId;
}
