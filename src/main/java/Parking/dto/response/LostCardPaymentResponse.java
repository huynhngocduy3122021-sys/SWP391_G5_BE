package Parking.dto.response;

import Parking.enums.PaymentMethod;
import Parking.enums.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class LostCardPaymentResponse {
    private Long incidentId;
    private Long paymentId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String transactionRef;
    private String paymentUrl;
    private String cashReceiptNumber;
    private Long cashCollectedByUserId;
    private LocalDateTime cashCollectedAt;
    private Long cashVerifiedByUserId;
    private LocalDateTime cashVerifiedAt;
}
