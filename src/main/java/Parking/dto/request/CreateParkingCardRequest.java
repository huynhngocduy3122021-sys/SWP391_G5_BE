package Parking.dto.request;

import Parking.enums.ParkingCardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateParkingCardRequest {
    @NotBlank(message = "Card code is required")
    @Size(min = 3, max = 50, message = "Card code must be between 3 and 50 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9\\-_]+$",
            message = "Card code can only contain letters, numbers, '-' or '_'"
    )
    private String cardCode;

    @NotNull(message = "ParkingBranchID cannot null")
    private Long parkingBranchId;

    private ParkingCardType type;
}
