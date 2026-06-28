package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LostCardIncidentRequest {
    @NotBlank(message = "Lý do/Mô tả báo mất thẻ không được để trống")
    private String description;

    @NotNull(message = "Phiên giữ xe hiện tại của xe không được để trống")
    private Long parkingSessionId;

    private String cardCode; // Tùy chọn (optional)
}
