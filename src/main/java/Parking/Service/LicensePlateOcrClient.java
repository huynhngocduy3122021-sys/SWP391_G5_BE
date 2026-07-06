package Parking.Service;

import Parking.dto.response.OcrLicensePlateResult;
import org.springframework.web.multipart.MultipartFile;

public interface LicensePlateOcrClient {
    OcrLicensePlateResult readLicensePlate(MultipartFile image);
}
