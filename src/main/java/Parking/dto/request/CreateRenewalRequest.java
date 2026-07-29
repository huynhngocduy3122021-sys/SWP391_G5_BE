package Parking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRenewalRequest {
    @NotNull(message = "ID gói dịch vụ là bắt buộc")
    private Long policyId;

    @NotNull(message = "ID chi nhánh là bắt buộc")
    private Long branchId;
}
