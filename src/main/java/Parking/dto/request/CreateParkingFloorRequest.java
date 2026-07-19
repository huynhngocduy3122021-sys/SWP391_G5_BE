package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateParkingFloorRequest {

    @NotBlank(message = "Tên tầng là bắt buộc")
    @Size(max = 100)
    private String floorName;

    @NotNull(message = "Số tầng là bắt buộc")
    private Integer floorNumber;

    @Size(max = 255)
    private String description;

    @NotNull(message = "ID chi nhánh bãi xe là bắt buộc")
    private Long parkingBranchId;
}
