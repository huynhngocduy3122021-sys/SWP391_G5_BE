package Parking.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MonthlyTicketResponse {

    @Getter
    @Setter
    @Builder
    public static class PricePolicySummary {
        private Long pricePolicyId;
        private String policyName;
        private Long vehicleTypeId;
        private String vehicleTypeName;
    }

    private Long ticketId;

    private Long vehicleId;

    private Long vehicleTypeId;

    private String licensePlate;

    private Long parkingCardId;

    private String cardCode;

    private String guestName;

    private String guestPhone;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Long parkingBranchId;

    private String parkingBranchName;

    private Long pricePolicyId;

    private PricePolicySummary pricePolicy;

    private Long monthlyTicketRequestId;

    private Integer status; // 1 = Active, 0 = Expired/Locked

    private LocalDateTime createdAt;
}
