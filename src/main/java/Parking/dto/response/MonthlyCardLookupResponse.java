package Parking.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MonthlyCardLookupResponse {
    private String lookupStatus;
    private String message;
    private Long ticketId;
    private Long parkingCardId;
    private String cardCode;
    private Long vehicleId;
    private String licensePlate;
    private String vehicleColor;
    private String vehicleBrand;
    private Long vehicleTypeId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
