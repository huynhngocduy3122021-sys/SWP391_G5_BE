package Parking.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BookingResponse {
    private Long bookingId;
    private String bookingCode;
    private Long userId;
    private String userFullName;
    private Long parkingBranchId;
    private String parkingBranchName;
    private Long vehicleId;
    private String licensePlate;
    private String vehicleColor;
    private String vehicleBrand;
    private Long vehicleTypeId;
    private String vehicleTypeName;
    private Long parkingSessionId;
    private LocalDateTime expectedArrivalTime;
    private LocalDateTime holdUntil;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiredAt;
}
