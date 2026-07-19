package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateParkingZoneRequest {

    @NotBlank(message = "Tên khu vực đỗ xe là bắt buộc")
    @Size(max = 100)
    private String zoneName;

    @NotNull(message = "Sức chứa là bắt buộc")
    @Positive(message = "Sức chứa phải lớn hơn 0")
    private Integer capacity;

    @NotNull(message = "ID tầng đỗ xe là bắt buộc")
    private Long parkingFloorId;

    @NotNull(message = "ID loại phương tiện là bắt buộc")
    private Long vehicleTypeId;
}