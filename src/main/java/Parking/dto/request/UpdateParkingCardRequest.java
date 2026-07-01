package Parking.dto.request;

import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingCardType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateParkingCardRequest {

    @Size(min = 3, max = 50, message = "Card code must be between 3 and 50 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9\\-_]+$",
            message = "Card code can only contain letters, numbers, '-' or '_'"
    )
    private String cardCode;

    private Long parkingBranchId;

    private ParkingCardStatus status;

    private ParkingCardType type;
}
