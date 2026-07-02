package Parking.dto.request;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMonthlyTicketRequest {

    private Long vehicleId;

    private Long parkingCardId;

    private String guestName;

    private String guestPhone;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Integer status; // 1 = Active, 0 = Expired/Locked
}
