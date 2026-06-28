package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class ResolveIncidentRequest {
    @NotBlank(message = "Ghi chú khắc phục sự cố không được để trống khi hoàn thành")
    private String resolutionNotes;

    private BigDecimal lostCardFee; // Ghi nhận phụ phí đền bù thẻ mất (nếu có)
}
