package Parking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MonthlyTicketRequestResponse {

    @Getter @Setter @Builder
    public static class VehicleSummary {
        private Long vehiclesId;
        private String licensePlate;
        private String vehicleColor;
        private String vehicleBrand;
        private Long vehicleTypeId;
        private String vehicleTypeName;
    }

    @Getter @Setter @Builder
    public static class UserSummary {
        private Long userId;
        private String userFullName;
        private String userEmail;
        private String userPhone;
    }

    @Getter @Setter @Builder
    public static class PricePolicySummary {
        private Long pricePolicyId;
        private String policyName;
        private BigDecimal basePrice;
    }

    @Getter @Setter @Builder
    public static class ParkingBranchSummary {
        private Long parkingBranchId;
        private String branchName;
    }

    @Getter @Setter @Builder
    public static class PaymentSummary {
        private Long paymentId;
        private BigDecimal amount;
        private String paymentMethod;
        private String paymentStatus;
        private String transactionRef;
        private String responseCode;
        private LocalDateTime createdAt;
        private LocalDateTime paidAt;
    }

    @Getter @Setter @Builder
    public static class RenewalSummary {
        private Long ticketId;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String status;
    }

    private Long id;
    private VehicleSummary vehicle;
    private UserSummary user;
    private PricePolicySummary pricePolicy;
    private ParkingBranchSummary parkingBranch;
    private String status;
    private Integer statusCode;
    private LocalDateTime createdAt;
    private PaymentSummary payment;
    private RenewalSummary renewalOfTicket;
}
