package Parking.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitMonthlyTicketRequest {
    private Long vehicleId;
    private Long policyId;
    private Long branchId;
}
