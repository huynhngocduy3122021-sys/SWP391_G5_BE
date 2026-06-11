package Parking.dto.request;

import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Setter
public class CreateVehicleTypeRequest {
    @NotBlank(message = "Type name is required")
    @Size(max = 100, message = "Type name must not exceed 100 characters")
    private String typeName;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;
}
