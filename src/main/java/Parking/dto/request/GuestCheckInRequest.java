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
public class GuestCheckInRequest {
     @NotBlank(message = "License plate is required")
    @Size(min = 5, max = 20, message = "License plate must be between 5 and 20 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9\\-.]+$",
            message = "License plate can only contain letters, numbers, '-' or '.'"
    )
    private String licensePlate;

    @NotNull(message = "Vehicle type id is required")
    @Positive(message = "Vehicle type id must be greater than 0")
    private Long vehicleTypeId;

    

    @NotBlank(message = "Card code is required")
    @Size(min = 3, max = 50, message = "Card code must be between 3 and 50 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9\\-_]+$",
            message = "Card code can only contain letters, numbers, '-' or '_'"
    )
    private String cardCode;

    private String vehicleColor;

    private String vehicleBranch;
}
