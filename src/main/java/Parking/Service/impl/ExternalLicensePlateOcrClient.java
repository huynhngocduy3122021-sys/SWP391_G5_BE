package Parking.Service.impl;

import Parking.Service.LicensePlateOcrClient;
import Parking.dto.response.OcrLicensePlateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ExternalLicensePlateOcrClient implements LicensePlateOcrClient {

    private final RestTemplate restTemplate;

    // URL của Plate Recognizer
    private final String OCR_API_URL = "https://api.platerecognizer.com/v1/plate-reader/";
    
    // API Token do người dùng cung cấp
    private final String API_TOKEN = "b3b2c18eee63c8bbc05b0ad03bd28fcfc04f84a0";

    public ExternalLicensePlateOcrClient() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public OcrLicensePlateResult readLicensePlate(MultipartFile image) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            // Gắn token xác thực của Plate Recognizer
            headers.set("Authorization", "Token " + API_TOKEN);

            ByteArrayResource imageResource = new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename() != null ? image.getOriginalFilename() : "image.jpg";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            // Plate Recognizer yêu cầu field tên là "upload"
            body.add("upload", imageResource); 
            // Cấu hình vùng Việt Nam để AI nhận diện chuẩn form biển số VN
            body.add("regions", "vn");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(OCR_API_URL, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                // Lấy mảng "results"
                List<Map<String, Object>> results = (List<Map<String, Object>>) responseBody.get("results");
                
                if (results != null && !results.isEmpty()) {
                    // Lấy kết quả đầu tiên (biển số rõ nhất)
                    Map<String, Object> firstResult = results.get(0);
                    
                    String plate = (String) firstResult.get("plate");
                    
                    Double confidence = 0.0;
                    if (firstResult.containsKey("score")) {
                        Object scoreObj = firstResult.get("score");
                        confidence = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0.0;
                    }

                    log.info("Plate Recognizer đọc được: {} với độ tin cậy: {}", plate, confidence);

                    return OcrLicensePlateResult.builder()
                            .licensePlate(plate)
                            .confidence(confidence)
                            .build();
                } else {
                    log.warn("Plate Recognizer không tìm thấy biển số nào trong ảnh");
                    return OcrLicensePlateResult.builder().confidence(0.0).build();
                }

            } else {
                log.error("API Plate Recognizer trả về lỗi với HTTP Status: {}", response.getStatusCode());
                return OcrLicensePlateResult.builder().confidence(0.0).build();
            }

        } catch (Exception e) {
            log.error("Có lỗi xảy ra khi gọi API Plate Recognizer", e);
            return OcrLicensePlateResult.builder().confidence(0.0).build();
        }
    }
}
