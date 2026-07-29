package Parking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApproveMonthlyTicketRequest {

    @NotNull(message = "ID thẻ giữ xe là bắt buộc")
    private Long parkingCardId;
}
