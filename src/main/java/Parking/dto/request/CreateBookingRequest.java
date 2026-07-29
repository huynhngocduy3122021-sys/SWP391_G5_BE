package Parking.dto.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateBookingRequest {

    @NotNull(message = "ID chi nhánh bãi xe là bắt buộc")
    private Long parkingBranchId;

    @NotNull(message = "ID loại phương tiện là bắt buộc")
    private Long vehicleTypeId;

    @NotBlank(message = "Biển số xe là bắt buộc")
    @Size(max = 20, message = "Biển số xe không được vượt quá 20 ký tự")
    private String licensePlate;

    @NotNull(message = "Thời gian dự kiến đến là bắt buộc")
    private LocalDateTime expectedArrivalTime;

    private String vehicleColor;

    private String vehicleBrand;
}
