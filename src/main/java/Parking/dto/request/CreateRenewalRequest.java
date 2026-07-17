package Parking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRenewalRequest {
    @NotNull(message = "Policy ID is required")
    private Long policyId;

    @NotNull(message = "Branch ID is required")
    private Long branchId;
}
