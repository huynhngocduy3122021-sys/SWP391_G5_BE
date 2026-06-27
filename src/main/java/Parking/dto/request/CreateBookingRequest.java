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

    @NotNull(message = "Parking branch ID is required")
    private Long parkingBranchId;

    @NotNull(message = "Vehicle type ID is required")
    private Long vehicleTypeId;

    @NotBlank(message = "License plate is required")
    @Size(max = 20, message = "License plate must not exceed 20 characters")
    private String licensePlate;

    @NotNull(message = "Expected arrival time is required")
    private LocalDateTime expectedArrivalTime;

    private String vehicleColor;

    private String vehicleBrand;
}
