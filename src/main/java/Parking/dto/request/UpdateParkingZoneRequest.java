package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateParkingZoneRequest {

    @NotBlank(message = "Zone name is required")
    @Size(max = 100)
    private String zoneName;

    @NotNull(message = "Capacity is required")
    @Positive(message = "Capacity must be greater than 0")
    private Integer capacity;

    @NotNull(message = "Parking floor id is required")
    private Long parkingFloorId;

    @NotNull(message = "Vehicle type id is required")
    private Long vehicleTypeId;
}