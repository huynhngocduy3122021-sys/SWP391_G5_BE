package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelIncidentRequest {
    @NotBlank(message = "Lý do hủy sự cố không được để trống")
    private String cancellationReason;
}
