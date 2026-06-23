package Parking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import Parking.enums.ParkingSessionStatus;
import Parking.enums.PaymentMethod;
import Parking.enums.PaymentStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@Builder
public class ParkingSessionResponse {
    
    private Long parkingSessionId;

    private Long parkingBranchId;

    private String parkingBranchName;

    private Long vehicleId;

    private String licensePlate;

    private String vehicleColor;

    private String vehicleBrand;

    private Long vehicleTypeId;

    private String vehicleTypeName;

    private Long parkingCardId;

    private String cardCode;

    private LocalDateTime checkInTime;

    private LocalDateTime checkOutTime;

    private BigDecimal totalAmount;

    private ParkingSessionStatus sessionStatus;

    private PaymentMethod paymentMethod;

    private PaymentStatus paymentStatus;
    
}
