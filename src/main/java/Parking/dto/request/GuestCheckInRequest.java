package Parking.dto.request;

import java.time.LocalDateTime;

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
     @NotBlank(message = "Biển số xe là bắt buộc")
    @Size(min = 5, max = 20, message = "Biển số xe phải có từ 5 đến 20 ký tự")
    @Pattern(
            regexp = "^[A-Za-z0-9\\-.]+$",
            message = "Biển số xe chỉ được chứa chữ cái, chữ số, dấu '-' hoặc dấu '.'"
    )
    private String licensePlate;

    @NotNull(message = "ID loại phương tiện là bắt buộc")
    @Positive(message = "ID loại phương tiện phải lớn hơn 0")
    private Long vehicleTypeId;

    

    @NotBlank(message = "Mã thẻ là bắt buộc")
    @Size(min = 3, max = 50, message = "Mã thẻ phải có từ 3 đến 50 ký tự")
    @Pattern(
            regexp = "^[A-Za-z0-9\\-_]+$",
            message = "Mã thẻ chỉ được chứa chữ cái, chữ số, dấu '-' hoặc dấu '_'"
    )
    private String cardCode;

    private String vehicleColor;

    private String vehicleBrand;

    private LocalDateTime time;
}
