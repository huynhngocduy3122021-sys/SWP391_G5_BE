package Parking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentReportResponse {
    private Long paymentId;
    private BigDecimal amount;
    private String paymentMethod;
    private String paymentStatus;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private String transactionRef;

    // Monthly Ticket Request info (if applicable)
    private Long monthlyTicketRequestId;
    private Integer monthlyTicketRequestStatus;
    private String policyName;
    private BigDecimal policyBasePrice;
    private String branchName;
    private Long branchId;
    private String vehicleLicensePlate;
    private String userName;

    // Parking Session info (if applicable)
    private Long parkingSessionId;
    private String sessionBranchName;
}
