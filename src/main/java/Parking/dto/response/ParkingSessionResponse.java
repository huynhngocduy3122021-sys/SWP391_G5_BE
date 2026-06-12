package Parking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class ParkingSessionResponse {
    
     private Long parkingSessionId;

    private String licensePlate;

    private String vehicleType;

    private String parkingZone;

    private String cardCode;

    private LocalDateTime checkInTime;

    private LocalDateTime checkOutTime;

    private String status;

    private BigDecimal totalAmount;
    
}
