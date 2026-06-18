package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateVehicleRequest {
    @NotBlank(message = "License plate is required")
    @Size(min = 5, max = 20, message = "License plate must be between 5 and 20 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9\\-.]+$",
            message = "License plate can only contain letters, numbers, '-' or '.'"
    )
    private String licensePlate;

    @Size(max = 255, message = "Vehicle color must not exceed 255 characters")
    private String vehicleColor;

    @Size(max = 100, message = "Vehicle brand must not exceed 100 characters")
    private String vehicleBrand;

    @NotNull(message = "Vehicle type id is required")
    @Positive(message = "Vehicle type id must be greater than 0")
    private Long vehicleTypeId;

    private Long userId;
}
