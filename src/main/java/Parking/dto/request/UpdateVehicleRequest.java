package Parking.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateVehicleRequest {
    @Size(min = 5, max = 20, message = "Biển số xe phải có từ 5 đến 20 ký tự")
    @Pattern(
            regexp = "^[A-Za-z0-9\\-.]+$",
            message = "Biển số xe chỉ được chứa chữ cái, chữ số, dấu '-' hoặc dấu '.'"
    )
    private String licensePlate;

    @Size(max = 255, message = "Màu phương tiện không được vượt quá 255 ký tự")
    private String vehicleColor;

    @Size(max = 100, message = "Thương hiệu phương tiện không được vượt quá 100 ký tự")
    private String vehicleBrand;

    @Positive(message = "ID loại phương tiện phải lớn hơn 0")
    private Long vehicleTypeId;

    private Long userId;
}
