package Parking.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MonthlyTicketResponse {

    private Long ticketId;

    private Long vehicleId;

    private String licensePlate;

    private Long parkingCardId;

    private String cardCode;

    private String guestName;

    private String guestPhone;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Long parkingBranchId;

    private String parkingBranchName;

    private Integer status; // 1 = Active, 0 = Expired/Locked

    private LocalDateTime createdAt;
}
