package Parking.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class CreatePricePolicyRequest {
    @NotBlank(message = "Policy name is required")
    @Size(max = 255, message = "Policy name must not exceed 255 characters")
    private String policyName;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Base price must be greater than 0")
    private BigDecimal basePrice;

    @NotNull(message = "Base duration minutes is required")
    @Positive(message = "Base duration minutes must be greater than 0")
    private Integer baseDurationMinutes;

    @NotNull(message = "Extra hour price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Extra hour price must be greater than or equal to 0")
    private BigDecimal extraHourPrice;

    private Integer extraDurationMinutes = 60;

    @NotNull(message = "Vehicle type id is required")
    @Positive(message = "Vehicle type id must be greater than 0")
    private Long vehicleTypeId;
}
