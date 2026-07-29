package Parking.dto.request;

import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Setter
public class CreateVehicleTypeRequest {
    @NotBlank(message = "Tên loại phương tiện là bắt buộc")
    @Size(max = 100, message = "Tên loại phương tiện không được vượt quá 100 ký tự")
    private String typeName;

    @Size(max = 255, message = "Mô tả không được vượt quá 255 ký tự")
    private String description;
}
