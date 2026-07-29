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

    @Size(min = 3, max = 50, message = "Mã thẻ phải có từ 3 đến 50 ký tự")
    @Pattern(
            regexp = "^[A-Za-z0-9\\-_]+$",
            message = "Mã thẻ chỉ được chứa chữ cái, chữ số, dấu '-' hoặc dấu '_'"
    )
    private String cardCode;

    private Long parkingBranchId;

    private ParkingCardStatus status;

    private ParkingCardType type;
}
