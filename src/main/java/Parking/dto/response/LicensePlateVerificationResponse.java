package Parking.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LicensePlateVerificationResponse {
    private String inputLicensePlate;
    private String detectedLicensePlate;
    private String normalizedInput;
    private String normalizedDetected;
    private boolean matched;
    private Double confidence;
    private String message;
}
