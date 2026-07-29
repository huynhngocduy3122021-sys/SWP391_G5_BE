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
    @NotBlank(message = "Mã thẻ là bắt buộc")
    @Size(min = 3, max = 50, message = "Mã thẻ phải có từ 3 đến 50 ký tự")
    @Pattern(
            regexp = "^[A-Za-z0-9\\-_]+$",
            message = "Mã thẻ chỉ được chứa chữ cái, chữ số, dấu '-' hoặc dấu '_'"
    )
    private String cardCode;

    @NotNull(message = "ID chi nhánh bãi xe không được để trống")
    private Long parkingBranchId;

    private ParkingCardType type;
}
