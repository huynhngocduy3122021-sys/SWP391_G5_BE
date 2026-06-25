package Parking.dto.response;

import java.math.BigDecimal;

import Parking.enums.ParkingSessionStatus;
import Parking.enums.PaymentMethod;
import Parking.enums.PaymentStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class GuestCheckOutResponse {
    
    private Long parkingSessionId;
    private Long paymentId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private ParkingSessionStatus sessionStatus;
    // nếu CASH sẽ bằng NULL , BANK_TRANFER = URL_VNPAY
    private String paymentUrl;
    private String message;
}
