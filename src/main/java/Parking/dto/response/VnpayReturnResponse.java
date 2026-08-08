package Parking.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import Parking.enums.PaymentStatus;

@Getter
@Setter
@Builder
public class VnpayReturnResponse {
    private boolean validSignature;
    private boolean success;
    private String transactionRef;
    private String vnpTransactionNo;
    private String responseCode;
    private String message;
    private String paymentType;
    private PaymentStatus paymentStatus;
    /** Báo cáo mất thẻ tương ứng với paymentType=LOST_CARD. */
    private Long incidentId;
    private Long requestId;
    private String licensePlate;
    private Long vehicleId;
    private String policyName;
    private Long policyId;
}
