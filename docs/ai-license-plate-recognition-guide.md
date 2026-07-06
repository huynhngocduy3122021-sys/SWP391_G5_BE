# Hướng dẫn chức năng AI đọc biển số từ ảnh và so khớp biển số nhập

## Mục tiêu

Chức năng cần làm:

1. Người dùng gửi ảnh biển số xe lên hệ thống.
2. Backend gọi OCR/AI để đọc biển số từ ảnh.
3. Backend chuẩn hóa biển số AI đọc được và biển số người dùng nhập.
4. Backend so sánh hai biển số và trả về kết quả đúng/sai.

Ví dụ:

- Biển số nhập: `51H-123.45`
- AI đọc từ ảnh: `51H12345`
- Sau khi chuẩn hóa: cả hai thành `51H12345`
- Kết quả: `matched = true`

## Hướng triển khai nên chọn

Nên làm theo 2 giai đoạn.

## API khuyến nghị cho dự án này

Nên dùng **Plate Recognizer Snapshot Cloud API** cho MVP.

Lý do chọn:

- Đây là API chuyên cho ALPR/ANPR, tức là chuyên đọc biển số xe, phù hợp hơn OCR tổng quát.
- Có hỗ trợ country code `vn` cho Việt Nam, nên có thể truyền `regions=vn` để tối ưu theo format biển số Việt Nam.
- Endpoint nhận ảnh qua `multipart/form-data` hoặc nhận URL ảnh qua `upload_url`.
- Project đã có Cloudinary, nên nên upload ảnh lên Cloudinary trước rồi gửi URL Cloudinary sang Plate Recognizer.
- Response có `plate`, `score`, `box`, `region`, giúp backend dễ lấy biển số và confidence để so khớp.
- Sau này nếu cần chạy nội bộ, Plate Recognizer cũng có hướng on-premise/SDK, không phải đổi toàn bộ kiến trúc.

Không nên chọn Google Vision làm lựa chọn chính cho use case này. Google Vision OCR mạnh cho text detection tổng quát, nhưng không chuyên cho biển số xe. Nếu dùng Google Vision, backend sẽ phải tự lọc text, tự đoán đâu là biển số, tự xử lý ảnh nghiêng/mờ/nhiều xe. Có thể dùng Google Vision làm phương án dự phòng, nhưng không nên là lựa chọn đầu tiên.

Tóm lại:

```text
Khuyến nghị chính: Plate Recognizer Snapshot Cloud API
Region nên truyền: vn
Fallback nếu cần: Google Cloud Vision TEXT_DETECTION
Không ưu tiên: OpenALPR bản cũ/self-host nếu team chưa có kinh nghiệm AI deployment
```

### Giai đoạn 1: MVP dùng OCR/AI API bên ngoài

Đây là hướng nhanh nhất để demo và tích hợp vào project Spring Boot hiện tại.

Các lựa chọn phổ biến:

- OCR API chuyên đọc biển số xe.
- Google Vision OCR / Azure AI Vision / AWS Textract.
- Tự gọi một service Python riêng nếu team đã có model.

Ưu điểm:

- Không cần train model.
- Code backend Java nhẹ hơn.
- Dễ hoàn thành chức năng so khớp biển số.

Nhược điểm:

- Có thể tốn phí theo số request.
- Phụ thuộc internet và API key.
- Cần kiểm tra độ chính xác với biển số Việt Nam.

### Giai đoạn 2: Tự host model AI

Khi cần kiểm soát chi phí hoặc chạy offline, có thể tách thành service riêng:

- Java Spring Boot vẫn là backend chính.
- Python FastAPI xử lý AI/OCR.
- Model có thể dùng hướng YOLO để detect vùng biển số, sau đó dùng OCR như PaddleOCR/EasyOCR để đọc text.

Luồng:

```text
Frontend -> Spring Boot -> Python AI Service -> Spring Boot -> Frontend
```

Không nên nhét trực tiếp model AI nặng vào Spring Boot ngay từ đầu, vì khó deploy và khó debug hơn.

## Luồng nên dùng với Cloudinary

Có thể upload ảnh lên Cloudinary rồi dùng phương pháp này để nhận diện biển số.

Luồng đề xuất:

```text
Frontend gửi ảnh + biển số nhập
-> Spring Boot upload ảnh lên Cloudinary
-> Cloudinary trả secure_url
-> Spring Boot gửi secure_url sang Plate Recognizer bằng upload_url
-> Plate Recognizer trả biển số đọc được
-> Spring Boot chuẩn hóa và so khớp
-> Lưu imageUrl + detectedPlate + confidence nếu cần audit
```

Nên dùng `secure_url` của Cloudinary vì Plate Recognizer cần truy cập được ảnh qua internet. Nếu ảnh Cloudinary bị private hoặc URL hết hạn quá nhanh, Plate Recognizer sẽ không đọc được.

Có 2 cách gửi ảnh sang Plate Recognizer:

| Cách | Khi nào dùng |
| --- | --- |
| Gửi file bytes bằng `upload` | Không cần lưu ảnh, chỉ verify tức thời |
| Gửi Cloudinary URL bằng `upload_url` | Nên dùng cho dự án này vì cần lưu ảnh check-in/check-out |

Với dự án bãi xe, nên chọn `upload_url`.

## API đề xuất

Tạo endpoint verify độc lập:

```http
POST /api/vehicles/verify-license-plate
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

Form data:

| Field | Type | Bắt buộc | Ý nghĩa |
| --- | --- | --- | --- |
| `licensePlate` | text | Có | Biển số người dùng nhập |
| `image` | file | Có | Ảnh biển số xe |

Response mẫu:

```json
{
  "inputLicensePlate": "51H-123.45",
  "detectedLicensePlate": "51H12345",
  "normalizedInput": "51H12345",
  "normalizedDetected": "51H12345",
  "matched": true,
  "confidence": 0.92,
  "message": "Biển số khớp"
}
```

Nếu tích hợp vào check-in/check-out, có thể dùng trực tiếp trong `ParkingSessionService` thay vì bắt frontend gọi riêng.

## Cấu trúc file nên thêm

Nên thêm các file sau:

```text
src/main/java/Parking/Controller/LicensePlateRecognitionController.java
src/main/java/Parking/Service/LicensePlateRecognitionService.java
src/main/java/Parking/Service/LicensePlateOcrClient.java
src/main/java/Parking/dto/response/LicensePlateVerificationResponse.java
src/main/java/Parking/dto/response/OcrLicensePlateResult.java
```

Nếu muốn gom vào `VehicleController` cũng được, nhưng nên tách controller riêng để chức năng AI không làm `VehicleController` bị phình to.

## DTO response đề xuất

```java
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
```

```java
package Parking.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OcrLicensePlateResult {
    private String licensePlate;
    private Double confidence;
}
```

## Controller mẫu

```java
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
```

## Service xử lý so khớp

```java
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
```

## OCR client

Tạo interface để sau này có thể đổi nhà cung cấp OCR mà không sửa logic so khớp:

```java
package Parking.Service;

import Parking.dto.response.OcrLicensePlateResult;
import org.springframework.web.multipart.MultipartFile;

public interface LicensePlateOcrClient {
    OcrLicensePlateResult readLicensePlate(MultipartFile image);
}
```

Sau đó tạo implementation cho API thật, ví dụ:

```text
src/main/java/Parking/Service/impl/ExternalLicensePlateOcrClient.java
```

Ban đầu có thể mock tạm để test luồng backend:

```java
package Parking.Service.impl;

import Parking.Service.LicensePlateOcrClient;
import Parking.dto.response.OcrLicensePlateResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExternalLicensePlateOcrClient implements LicensePlateOcrClient {
    @Override
    public OcrLicensePlateResult readLicensePlate(MultipartFile image) {
        return OcrLicensePlateResult.builder()
                .licensePlate("51H12345")
                .confidence(0.95)
                .build();
    }
}
```

Khi đã chọn OCR API thật, thay phần mock bằng code gọi HTTP.

## Cấu hình Plate Recognizer

Thêm biến môi trường hoặc cấu hình trong `application.properties`:

```properties
plate-recognizer.api-url=https://api.platerecognizer.com/v1/plate-reader/
plate-recognizer.api-token=YOUR_API_TOKEN
plate-recognizer.region=vn
```

Không nên commit API token thật lên Git.

Request gửi sang Plate Recognizer:

```http
POST https://api.platerecognizer.com/v1/plate-reader/
Authorization: Token YOUR_API_TOKEN
Content-Type: multipart/form-data
```

Form data khi gửi trực tiếp file:

```text
upload = file ảnh
regions = vn
```

Form data khi dùng ảnh đã upload Cloudinary:

```text
upload_url = https://res.cloudinary.com/.../license-plate.jpg
regions = vn
```

Response của Plate Recognizer thường có danh sách `results`. Backend nên lấy biển số có `score` cao nhất:

```json
{
  "results": [
    {
      "plate": "51h12345",
      "score": 0.92,
      "region": {
        "code": "vn",
        "score": 0.88
      }
    }
  ]
}
```

Mapping về DTO nội bộ:

```java
OcrLicensePlateResult.builder()
        .licensePlate(bestResult.getPlate())
        .confidence(bestResult.getScore())
        .build();
```

Nếu `results` rỗng, trả lỗi nghiệp vụ hoặc response:

```json
{
  "matched": false,
  "message": "AI không đọc được biển số, vui lòng chụp lại ảnh"
}
```

## Gợi ý code gọi Plate Recognizer bằng Cloudinary URL

Spring Boot 3 có thể dùng `RestClient` để gọi API ngoài.

```java
package Parking.Service.impl;

import Parking.Service.LicensePlateOcrClient;
import Parking.dto.response.OcrLicensePlateResult;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class PlateRecognizerOcrClient implements LicensePlateOcrClient {
    private final RestClient.Builder restClientBuilder;

    @Value("${plate-recognizer.api-url}")
    private String apiUrl;

    @Value("${plate-recognizer.api-token}")
    private String apiToken;

    @Value("${plate-recognizer.region:vn}")
    private String region;

    public OcrLicensePlateResult readLicensePlateFromUrl(String imageUrl) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("upload_url", imageUrl);
        body.add("regions", region);

        PlateRecognizerResponse response = restClientBuilder.build()
                .post()
                .uri(apiUrl)
                .header("Authorization", "Token " + apiToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(PlateRecognizerResponse.class);

        if (response == null || response.results() == null || response.results().isEmpty()) {
            throw new ParkingSessionException("AI không đọc được biển số, vui lòng chụp lại ảnh");
        }

        PlateRecognizerResult bestResult = response.results().stream()
                .max(Comparator.comparing(PlateRecognizerResult::score))
                .orElseThrow(() -> new ParkingSessionException("AI không đọc được biển số, vui lòng chụp lại ảnh"));

        return OcrLicensePlateResult.builder()
                .licensePlate(bestResult.plate())
                .confidence(bestResult.score())
                .build();
    }

    private record PlateRecognizerResponse(
            java.util.List<PlateRecognizerResult> results
    ) {
    }

    private record PlateRecognizerResult(
            String plate,
            Double score
    ) {
    }
}
```

Nếu vẫn muốn giữ interface `readLicensePlate(MultipartFile image)`, service có thể:

1. Upload `MultipartFile` lên Cloudinary.
2. Lấy `secure_url`.
3. Gọi `readLicensePlateFromUrl(secureUrl)`.

Lưu ý: nếu project chưa có bean `RestClient.Builder`, thêm config:

```java
package Parking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
```

## Cấu hình upload file

Trong `application.properties` hoặc `application.yml`, nên giới hạn dung lượng ảnh:

```properties
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=5MB
```

Nên chỉ nhận các định dạng:

- `image/jpeg`
- `image/png`
- `image/webp`

Có thể validate trong service:

```java
private void validateImage(MultipartFile image) {
    String contentType = image.getContentType();
    if (contentType == null ||
            (!contentType.equals("image/jpeg")
                    && !contentType.equals("image/png")
                    && !contentType.equals("image/webp"))) {
        throw new ParkingSessionException("Ảnh biển số phải là JPG, PNG hoặc WEBP");
    }
}
```

## Cách gọi từ frontend

Frontend gửi `FormData`:

```javascript
const formData = new FormData();
formData.append("licensePlate", licensePlate);
formData.append("image", imageFile);

const response = await fetch("/api/license-plate/verify", {
  method: "POST",
  headers: {
    Authorization: `Bearer ${token}`
  },
  body: formData
});

const result = await response.json();
```

Không set thủ công `Content-Type` khi dùng `FormData`, vì browser sẽ tự thêm boundary.

## Quy tắc chuẩn hóa biển số

Nên dùng chung một hàm chuẩn hóa cho toàn bộ hệ thống:

```java
private String normalizeLicensePlate(String licensePlate) {
    return licensePlate
            .trim()
            .toUpperCase()
            .replaceAll("[^A-Z0-9]", "");
}
```

Lý do:

- Người dùng có thể nhập `51H-123.45`.
- AI có thể đọc `51H12345`.
- Database có thể lưu `51H 12345`.

Sau chuẩn hóa, các format trên đều trở thành `51H12345`.

## Các lỗi cần xử lý

| Tình huống | Cách xử lý |
| --- | --- |
| Không gửi ảnh | Trả lỗi `Ảnh biển số không được để trống` |
| Không nhập biển số | Trả lỗi `Biển số nhập không được để trống` |
| File không phải ảnh | Trả lỗi `Ảnh biển số phải là JPG, PNG hoặc WEBP` |
| OCR không đọc được | Trả `matched = false`, message yêu cầu chụp lại |
| OCR confidence thấp | Trả `matched = false`, message yêu cầu chụp lại |
| OCR đọc khác biển nhập | Trả `matched = false` |
| OCR đọc giống biển nhập | Trả `matched = true` |

## Gợi ý tích hợp với nghiệp vụ gửi xe

Sau khi API verify hoạt động ổn, có thể dùng vào các luồng:

1. Check-in xe khách:
   - Người dùng nhập biển số.
   - Chụp ảnh biển số.
   - Upload ảnh check-in lên Cloudinary.
   - Gửi `secure_url` sang Plate Recognizer.
   - Nếu AI đọc khớp biển số nhập thì cho tạo parking session.
   - Lưu `entryImageUrl`, `entryDetectedPlate`, `entryPlateConfidence`.

2. Check-out xe khách:
   - Nhận diện được.
   - Chụp ảnh biển số lúc xe ra.
   - Upload ảnh check-out lên Cloudinary.
   - Gửi `secure_url` sang Plate Recognizer.
   - So biển số AI đọc lúc ra với biển số trong `ParkingSession` hoặc biển số xe đã check-in.
   - Nếu khớp thì cho thanh toán/check-out.
   - Nếu không khớp hoặc confidence thấp thì yêu cầu nhân viên xác nhận thủ công.
   - Lưu `exitImageUrl`, `exitDetectedPlate`, `exitPlateConfidence`.

3. Vé tháng:
   - So ảnh biển số với biển số xe đã đăng ký trong monthly ticket.
   - Nếu không khớp thì từ chối hoặc yêu cầu xác minh.

Kết luận: check-out nhận diện được, miễn là ảnh lúc ra đủ rõ và Cloudinary URL public để Plate Recognizer truy cập được.

## Checklist triển khai

1. Tạo DTO `LicensePlateVerificationResponse`.
2. Tạo DTO `OcrLicensePlateResult`.
3. Tạo interface `LicensePlateOcrClient`.
4. Tạo service `LicensePlateRecognitionService`.
5. Tạo controller `LicensePlateRecognitionController`.
6. Thêm validate ảnh.
7. Mock OCR để test API trước.
8. Tích hợp OCR API thật.
9. Test bằng Swagger/Postman với `multipart/form-data`.
10. Gắn API vào luồng check-in/check-out nếu cần.

## Test nhanh bằng Postman

Method:

```http
POST
```

URL:

```text
http://localhost:8080/api/license-plate/verify
```

Body chọn `form-data`:

```text
licensePlate = 51H-123.45
image = chọn file ảnh
```

Header:

```text
Authorization = Bearer <token>
```

Kết quả mong muốn:

```json
{
  "matched": true,
  "message": "Biển số khớp"
}
```
