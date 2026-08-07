package Parking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReplaceLostMonthlyCardRequest {
    @NotNull(message = "Phải chọn thẻ RFID thay thế")
    private Long replacementCardId;
}
