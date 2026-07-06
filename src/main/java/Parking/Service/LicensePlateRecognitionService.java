package Parking.Service;

import Parking.dto.response.LicensePlateVerificationResponse;
import Parking.dto.response.OcrLicensePlateResult;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LicensePlateRecognitionService {
    private static final double MIN_CONFIDENCE = 0.75;

    private final LicensePlateOcrClient ocrClient;

    public LicensePlateVerificationResponse verifyLicensePlate(String inputPlate, MultipartFile image) {
        if (inputPlate == null || inputPlate.isBlank()) {
            throw new ParkingSessionException("Biển số nhập không được để trống");
        }
        if (image == null || image.isEmpty()) {
            throw new ParkingSessionException("Ảnh biển số không được để trống");
        }

        validateImage(image);

        OcrLicensePlateResult ocrResult = ocrClient.readLicensePlate(image);
        String normalizedInput = normalizeLicensePlate(inputPlate);
        String normalizedDetected = normalizeLicensePlate(ocrResult.getLicensePlate());

        boolean confidenceOk = ocrResult.getConfidence() == null || ocrResult.getConfidence() >= MIN_CONFIDENCE;
        boolean matched = confidenceOk && normalizedInput.equals(normalizedDetected);

        return LicensePlateVerificationResponse.builder()
                .inputLicensePlate(inputPlate)
                .detectedLicensePlate(ocrResult.getLicensePlate())
                .normalizedInput(normalizedInput)
                .normalizedDetected(normalizedDetected)
                .matched(matched)
                .confidence(ocrResult.getConfidence())
                .message(buildMessage(matched, confidenceOk))
                .build();
    }

    private void validateImage(MultipartFile image) {
        String contentType = image.getContentType();
        if (contentType == null ||
                (!contentType.equals("image/jpeg")
                        && !contentType.equals("image/png")
                        && !contentType.equals("image/webp"))) {
            throw new ParkingSessionException("Ảnh biển số phải là JPG, PNG hoặc WEBP");
        }
    }

    private String normalizeLicensePlate(String licensePlate) {
        if (licensePlate == null) {
            return "";
        }
        return licensePlate
                .trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9]", "");
    }

    private String buildMessage(boolean matched, boolean confidenceOk) {
        if (!confidenceOk) {
            return "AI đọc biển số chưa đủ tin cậy, vui lòng chụp lại ảnh";
        }
        return matched ? "Biển số khớp" : "Biển số không khớp";
    }
}
