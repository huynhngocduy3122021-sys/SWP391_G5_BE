package Parking.dto.request;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMonthlyTicketRequest {

    @NotNull(message = "ID phương tiện là bắt buộc")
    private Long vehicleId;

    @NotNull(message = "ID thẻ giữ xe là bắt buộc")
    private Long parkingCardId;

    private String guestName;

    private String guestPhone;

    @NotNull(message = "Ngày bắt đầu là bắt buộc")
    private LocalDateTime startDate;

    @NotNull(message = "Ngày kết thúc là bắt buộc")
    private LocalDateTime endDate;

    @NotNull(message = "Trạng thái là bắt buộc")
    private Integer status; // 1 = Active, 0 = Expired/Locked
}
