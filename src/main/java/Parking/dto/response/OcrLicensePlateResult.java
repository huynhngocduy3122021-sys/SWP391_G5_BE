package Parking.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OcrLicensePlateResult {
    private String licensePlate;
    private Double confidence;
}
