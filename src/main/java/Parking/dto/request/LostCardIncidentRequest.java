package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import Parking.enums.LostCardStage;
import Parking.enums.ParkingCardType;

@Getter
@Setter
public class LostCardIncidentRequest {
    @NotBlank(message = "Lý do/Mô tả báo mất thẻ không được để trống")
    private String description;

    private Long parkingSessionId;

    /** Bắt buộc khi xe chưa có phiên gửi xe. */
    private Long parkingCardId;

    /** Bắt buộc khi xe chưa có phiên; dùng để xác định chi nhánh. */
    private Long parkingBranchId;

    private String cardCode;

    /** Nếu bỏ trống, backend suy ra từ thẻ được xác minh. */
    private ParkingCardType cardType;

    /** Nếu bỏ trống, backend suy ra từ việc có session ACTIVE hay không. */
    private LostCardStage lostStage;
}
