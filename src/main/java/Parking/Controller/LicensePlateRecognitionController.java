package Parking.Controller;

import Parking.Service.LicensePlateRecognitionService;
import Parking.dto.response.LicensePlateVerificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@SecurityRequirement(name = "api_key")
@RequestMapping("/api/license-plate")
@RequiredArgsConstructor
@CrossOrigin("*")
public class LicensePlateRecognitionController {
    private final LicensePlateRecognitionService recognitionService;

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Đọc biển số từ ảnh và so khớp với biển số nhập")
    public ResponseEntity<LicensePlateVerificationResponse> verifyLicensePlate(
            @RequestParam String licensePlate,
            @RequestParam MultipartFile image
    ) {
        return ResponseEntity.ok(recognitionService.verifyLicensePlate(licensePlate, image));
    }
}
