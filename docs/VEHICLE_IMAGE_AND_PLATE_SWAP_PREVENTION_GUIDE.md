# Hướng dẫn Frontend và Backend chống đổi biển số trong bãi

## 1. Mục tiêu

Tài liệu này hướng dẫn điều chỉnh luồng check-in/check-out để xử lý trường hợp:

1. Khách vào bãi bằng hình thức khách vãng lai.
2. Trong bãi, khách thay biển số hoặc đổi cả biển số và thẻ với xe đã đăng ký vé tháng.
3. Khách cố sử dụng biển số/vé tháng lúc ra để tránh phí của phiên gửi xe Guest.

Nguyên tắc bắt buộc:

> Quyền vé tháng và cách tính phí được xác định từ `ParkingSession` hợp lệ lúc xe vào, không được xác định lại chỉ bằng biển số OCR lúc xe ra.

Ảnh biển số và ảnh tổng quan có mục đích khác nhau:

- Ảnh biển số được gửi sang dịch vụ OCR/Plate Recognizer.
- Ảnh tổng quan chỉ được lưu làm bằng chứng và dùng để đối chiếu phương tiện; không gửi sang API nhận diện biển số.

### Ngoài phạm vi: ảnh báo cáo sự cố

Tài liệu này chỉ áp dụng cho `VehicleImage` gắn với `ParkingSession` trong luồng check-in/check-out. Nó không thay đổi entity, table, API hoặc quy trình của `IncidentImage`.

Ảnh báo cáo sự cố phải sử dụng luồng riêng được mô tả tại:

`docs/INCIDENT_IMAGE_UPLOAD_IMPLEMENTATION_GUIDE.md`

Không lưu ảnh sự cố vào bảng ảnh phương tiện, không dùng `VehicleImageType` để phân loại ảnh sự cố và không tự gửi ảnh sự cố sang dịch vụ OCR biển số.

## 2. Hiện trạng trong dự án

Các thành phần hiện có:

- `ParkingSessionService.guestCheckIn()` tạo phiên gửi xe và gắn xe với thẻ.
- `ParkingSessionService.guestCheckOut()` tìm session đang hoạt động theo `cardCode`, sau đó so biển số lúc ra với biển số trong session.
- `LicensePlateRecognitionController` nhận một ảnh qua `/api/license-plate/verify`.
- `VehicleImageController` nhận danh sách ảnh cùng một `imageType`.
- `VehicleImageType` hiện có `CHECK_IN`, `CHECK_OUT`, `LICENSE_PLATE`.

Các vấn đề cần sửa:

1. Một request upload nhiều ảnh nhưng chỉ có một `imageType`, nên backend không xác định chắc chắn ảnh nào là ảnh biển số và ảnh nào là ảnh tổng quan.
2. Frontend tự truyền `imageType`, có thể gửi nhầm hoặc cố tình gắn sai loại.
3. Check-out hiện chủ yếu đối chiếu `cardCode + licensePlate`; nếu hai xe đổi cả thẻ lẫn biển số thì có thể qua mặt việc so chuỗi biển số.
4. Nếu frontend gọi OCR và check-in/check-out thành các request độc lập, frontend có thể bỏ qua bước OCR hoặc gửi kết quả đã bị sửa.
5. Không được chuyển một session Guest thành Monthly tại cổng ra chỉ vì biển số lúc ra thuộc một vé tháng.

## 3. Business rule thống nhất

### 3.1. Khi check-in

Backend phải:

1. Nhận thẻ, thông tin xe, ảnh biển số và ảnh tổng quan.
2. Chỉ gửi `licensePlateImage` sang OCR.
3. So biển số OCR với biển số nhân viên nhập hoặc biển số đăng ký.
4. Xác định loại phiên gửi xe từ thẻ và vé tại thời điểm check-in.
5. Nếu là thẻ tháng:
   - thẻ có vé tháng còn hiệu lực;
   - biển số OCR khớp xe của vé tháng;
   - loại xe khớp;
   - xe chưa có session đang hoạt động.
6. Nếu là thẻ lượt:
   - tạo session Guest;
   - ghi lại biển số đã xác minh;
   - phí sau này phải tính theo session Guest.
7. Lưu cả hai ảnh với đúng mục đích.
8. Chỉ trả trạng thái cho phép mở barrier sau khi session và bằng chứng check-in đã được lưu thành công.

### 3.2. Khi check-out

Backend phải:

1. Tìm `ParkingSession ACTIVE` bằng `cardCode`.
2. Không tìm vé tháng mới theo biển số để thay đổi quyền của session.
3. Chỉ gửi ảnh biển số lúc ra sang OCR.
4. So biển số OCR lúc ra với biển số đã xác minh lúc vào.
5. Đối chiếu thêm dữ liệu tổng quan:
   - loại xe;
   - màu xe;
   - hãng/dòng xe;
   - ảnh tổng quan check-in và check-out.
6. Nếu bằng chứng khớp, tiếp tục tính phí/thanh toán theo session.
7. Nếu không khớp, giữ barrier đóng và chuyển sang xác minh thủ công.

### 3.3. Khi phát hiện không khớp

Các trường hợp sau phải chuyển `MANUAL_REVIEW`:

- OCR lúc ra khác biển số lúc vào.
- Biển số khớp nhưng ảnh tổng quan cho thấy xe khác.
- Thẻ thuộc session của xe khác.
- Loại xe khác.
- OCR không đọc được hoặc confidence thấp.
- Thiếu một trong hai ảnh bắt buộc.

Khi đó:

- không tự động mở barrier;
- không tự đổi Guest thành Monthly;
- không tự gắn session sang xe khác;
- lưu ảnh, biển số OCR, confidence và lý do;
- thông báo nhân viên kiểm tra;
- có thể tạo `IncidentReport` cho trường hợp nghi thay/giả biển số;
- sau khi xác minh, nhân viên có quyền tiếp tục checkout nhưng phí vẫn lấy từ session ban đầu.

## 4. Thay đổi Backend

### 4.1. Phân loại hình ảnh

Phương án nên dùng là tách thời điểm chụp và mục đích ảnh:

```java
public enum CaptureStage {
    CHECK_IN,
    CHECK_OUT
}
```

```java
public enum VehicleImagePurpose {
    LICENSE_PLATE,
    OVERVIEW
}
```

Thêm vào `VehicleImage`:

```java
@Enumerated(EnumType.STRING)
@Column(name = "capture_stage", nullable = false)
private CaptureStage captureStage;

@Enumerated(EnumType.STRING)
@Column(name = "image_purpose", nullable = false)
private VehicleImagePurpose imagePurpose;

@Column(name = "ocr_plate", length = 20)
private String ocrPlate;

@Column(name = "ocr_confidence")
private Double ocrConfidence;
```

Nếu chưa muốn migration lớn, có thể thay enum hiện tại bằng bốn giá trị:

```java
CHECK_IN_LICENSE_PLATE,
CHECK_IN_OVERVIEW,
CHECK_OUT_LICENSE_PLATE,
CHECK_OUT_OVERVIEW
```

Phương án tách hai enum dễ truy vấn và mở rộng hơn.

### 4.2. Trạng thái xác minh

Nên bổ sung trạng thái xác minh riêng, không nhất thiết trộn vào `ParkingSessionStatus`:

```java
public enum VehicleVerificationStatus {
    PENDING,
    MATCHED,
    MANUAL_REVIEW,
    APPROVED_BY_STAFF,
    REJECTED
}
```

Các trường nên lưu tại session hoặc bảng `vehicle_verifications`:

```text
parking_session_id
capture_stage
expected_plate
detected_plate
ocr_confidence
verification_status
failure_reason
verified_by
verified_at
```

### 4.3. API check-in mới

Không nên để frontend tự gọi OCR rồi tự quyết định có gọi check-in hay không. Backend phải điều phối toàn bộ.

Đề xuất:

```http
POST /api/parking-sessions/guest/check-in-with-evidence
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

Các part:

```text
data                 JSON của GuestCheckInRequest
licensePlateImage    file ảnh cận biển số
overviewImage        file ảnh tổng quan phương tiện
```

Controller minh họa:

```java
@PostMapping(
    value = "/guest/check-in-with-evidence",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
)
public ResponseEntity<ParkingSessionResponse> checkIn(
        @Valid @RequestPart("data") GuestCheckInRequest request,
        @RequestPart("licensePlateImage") MultipartFile licensePlateImage,
        @RequestPart("overviewImage") MultipartFile overviewImage
) {
    return ResponseEntity.ok(
        parkingSessionEvidenceService.checkIn(
            request,
            licensePlateImage,
            overviewImage
        )
    );
}
```

Service thực hiện theo thứ tự:

```java
validateImage(licensePlateImage);
validateImage(overviewImage);

LicensePlateVerificationResponse plateResult =
        recognitionService.verifyLicensePlate(
                request.getLicensePlate(),
                licensePlateImage
        );

if (!plateResult.isMatched()) {
    throw new ParkingSessionException(
            "Ảnh biển số không khớp hoặc độ tin cậy không đủ"
    );
}

ParkingSessionResponse session = parkingSessionService.guestCheckIn(request);

vehicleImageService.uploadEvidence(
        session.getParkingSessionId(),
        CaptureStage.CHECK_IN,
        licensePlateImage,
        overviewImage,
        plateResult
);
```

Việc tạo session và lưu metadata database nên nằm trong một transaction. Riêng upload Cloudinary là external I/O nên cần cleanup ảnh nếu database rollback, tương tự cách `VehicleImageService` hiện đang xóa `publicId` khi lưu thất bại.

### 4.4. API check-out mới

Đề xuất:

```http
POST /api/parking-sessions/guest/check-out-with-evidence
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

Các part:

```text
data                 JSON của GuestCheckOutRequest
licensePlateImage    ảnh cận biển số lúc ra
overviewImage        ảnh tổng quan lúc ra
```

Backend:

1. Tìm session bằng `cardCode`.
2. Lấy `expectedPlate` từ vehicle/session lúc vào.
3. Gửi duy nhất `licensePlateImage` sang OCR.
4. Lưu cả hai ảnh.
5. Nếu OCR không khớp, trả trạng thái `MANUAL_REVIEW` và không gọi thanh toán.
6. Nếu khớp, gọi `PaymentService.processCheckOutPayment()`.

Không dùng biển số OCR lúc ra để tìm và áp dụng một vé tháng khác.

### 4.5. Service upload ảnh không nhận danh sách không phân loại

Không dùng API dạng:

```java
uploadVehicleImages(sessionId, imageType, List<MultipartFile> files)
```

cho luồng cổng vào/ra vì hai file có hai mục đích khác nhau.

Nên tạo method:

```java
public List<VehicleImageResponse> uploadEvidence(
        Long sessionId,
        CaptureStage stage,
        MultipartFile licensePlateImage,
        MultipartFile overviewImage,
        LicensePlateVerificationResponse ocrResult
)
```

Trong method:

```java
uploadOne(
    session,
    stage,
    VehicleImagePurpose.LICENSE_PLATE,
    licensePlateImage,
    ocrResult
);

uploadOne(
    session,
    stage,
    VehicleImagePurpose.OVERVIEW,
    overviewImage,
    null
);
```

Chỉ nhánh `LICENSE_PLATE` được phép chứa `ocrPlate` và `ocrConfidence`.

### 4.6. So sánh ảnh tổng quan

Giai đoạn đầu không cần dùng một AI khác để so ảnh tổng quan. Có thể:

- hiển thị ảnh vào và ảnh ra cạnh nhau cho nhân viên;
- so loại xe, màu và hãng đã lưu;
- chỉ yêu cầu kiểm tra thủ công khi OCR/thuộc tính không khớp.

Nếu sau này dùng AI nhận dạng phương tiện, phải tạo service riêng. Không gửi ảnh tổng quan sang Plate Recognizer vì API này chuyên nhận dạng biển số, không chứng minh hai ảnh là cùng một xe.

### 4.7. Phân quyền xác minh thủ công

Endpoint xác minh phải giới hạn cho `STAFF`, `MANAGER` hoặc `ADMIN`:

```http
POST /api/parking-sessions/{sessionId}/verification/approve
```

Payload:

```json
{
  "reason": "Đã đối chiếu giấy tờ và ảnh phương tiện",
  "continueCheckout": true
}
```

Backend phải lưu:

- người duyệt;
- thời gian duyệt;
- lý do;
- biển số OCR;
- ảnh bằng chứng;
- phí Guest ban đầu.

Không cho frontend tự gửi `verificationStatus = APPROVED_BY_STAFF` trong request checkout thông thường.

## 5. Thay đổi Frontend

### 5.1. Giao diện chụp ảnh

Tại cả check-in và check-out, giao diện có hai vùng riêng:

1. **Ảnh biển số**
   - chụp cận biển;
   - biển số phải rõ và chiếm phần chính của ảnh;
   - bắt buộc.
2. **Ảnh tổng quan**
   - chụp toàn bộ xe;
   - nhìn thấy màu, loại, hình dáng và đặc điểm xe;
   - bắt buộc.

Không dùng một input `multiple` rồi dựa vào thứ tự file. Dùng hai input/state riêng:

```ts
const [licensePlateImage, setLicensePlateImage] =
    useState<File | null>(null);

const [overviewImage, setOverviewImage] =
    useState<File | null>(null);
```

### 5.2. Gửi multipart đúng field

Ví dụ:

```ts
const formData = new FormData();

formData.append(
  "data",
  new Blob([JSON.stringify(checkInData)], {
    type: "application/json",
  })
);

formData.append("licensePlateImage", licensePlateImage);
formData.append("overviewImage", overviewImage);

await api.post(
  "/api/parking-sessions/guest/check-in-with-evidence",
  formData
);
```

Frontend không gọi Plate Recognizer trực tiếp và không truyền ảnh tổng quan vào field `licensePlateImage`.

### 5.3. Validation trước khi gửi

Frontend kiểm tra:

- đủ hai ảnh;
- file là JPG, PNG hoặc WEBP;
- dung lượng mỗi ảnh không vượt quá giới hạn backend;
- hiển thị preview đúng nhãn;
- cho phép chụp lại từng ảnh riêng;
- không cho bấm check-in/check-out nhiều lần khi request đang xử lý.

Validation frontend chỉ hỗ trợ trải nghiệm; backend vẫn phải kiểm tra lại toàn bộ.

### 5.4. Xử lý response

Response nên có:

```json
{
  "parkingSessionId": 123,
  "verificationStatus": "MATCHED",
  "expectedPlate": "30A12345",
  "detectedPlate": "30A12345",
  "confidence": 0.94,
  "barrierAction": "OPEN",
  "message": "Xác minh phương tiện thành công"
}
```

Khi không khớp:

```json
{
  "parkingSessionId": 123,
  "verificationStatus": "MANUAL_REVIEW",
  "expectedPlate": "30A12345",
  "detectedPlate": "29B56789",
  "confidence": 0.96,
  "barrierAction": "KEEP_CLOSED",
  "message": "Biển số lúc ra không khớp với lúc vào"
}
```

Frontend phải dựa vào `barrierAction`/`verificationStatus` từ backend:

- `OPEN`: gửi lệnh mở barrier.
- `KEEP_CLOSED`: không mở barrier, hiển thị màn hình xác minh.
- Không tự suy ra mở barrier chỉ vì `matched = true` ở một request OCR riêng.

### 5.5. Màn hình xác minh thủ công

Hiển thị:

- ảnh biển số lúc vào và lúc ra;
- ảnh tổng quan lúc vào và lúc ra;
- biển số mong đợi;
- biển số OCR;
- confidence;
- thông tin thẻ;
- loại session `GUEST` hoặc `MONTHLY`;
- màu, hãng, loại xe;
- phí của session Guest nếu có;
- lý do cảnh báo.

Nút duyệt chỉ hiển thị cho role hợp lệ và bắt buộc nhập lý do.

## 6. Quy tắc tính phí chống gian lận

Ví dụ xe Guest vào bãi rồi thay biển Monthly:

```text
Session lúc vào: GUEST
Biển số lúc vào: 30A-GUEST
Biển số OCR lúc ra: 30A-MONTHLY
Kết quả: MANUAL_REVIEW
Phí: vẫn tính theo session GUEST
```

Ngay cả khi nhân viên xác minh và cho xe ra, backend không được:

- đổi session thành Monthly;
- gắn session sang MonthlyTicket;
- đặt phí bằng 0 chỉ vì biển số lúc ra thuộc xe tháng.

Nếu cần miễn/giảm phí đặc biệt, phải đi qua một nghiệp vụ override riêng, có role, lý do và audit log.

## 7. Mã lỗi/response đề xuất

| HTTP | Code | Ý nghĩa |
|---|---|---|
| 400 | `MISSING_LICENSE_PLATE_IMAGE` | Thiếu ảnh biển số |
| 400 | `MISSING_OVERVIEW_IMAGE` | Thiếu ảnh tổng quan |
| 400 | `INVALID_IMAGE` | File không hợp lệ |
| 409 | `PLATE_MISMATCH` | Biển số vào và ra không khớp |
| 409 | `VEHICLE_MISMATCH` | Thông tin/ảnh tổng quan nghi là xe khác |
| 409 | `CARD_SESSION_MISMATCH` | Thẻ không thuộc phiên của xe |
| 422 | `OCR_LOW_CONFIDENCE` | OCR chưa đủ tin cậy |
| 423 | `MANUAL_REVIEW_REQUIRED` | Phải xác minh thủ công |

Không trả HTTP 200 kèm message lỗi nếu barrier phải đóng. Frontend cần phân biệt rõ thành công và yêu cầu xác minh.

## 8. Migration dữ liệu

Nếu tách `captureStage` và `imagePurpose`, cần migration dữ liệu cũ:

- `CHECK_IN` → `capture_stage = CHECK_IN`, `image_purpose = OVERVIEW` nếu không xác định được chính xác.
- `CHECK_OUT` → `capture_stage = CHECK_OUT`, `image_purpose = OVERVIEW`.
- `LICENSE_PLATE` cần đối chiếu session/thời gian để xác định stage; nếu không chắc chắn, để trạng thái migration cần kiểm tra.

Không tự gán toàn bộ ảnh `LICENSE_PLATE` cũ thành check-in nếu dữ liệu không chứng minh được.

## 9. Kiểm thử Backend

1. Check-in đủ hai ảnh, OCR khớp: tạo session và lưu đúng hai purpose.
2. Chỉ ảnh biển số được gửi tới mock OCR client.
3. Ảnh tổng quan không bao giờ được gửi tới OCR client.
4. Thiếu một ảnh: không tạo session.
5. OCR confidence thấp: không mở barrier.
6. Monthly check-in nhưng biển số không khớp vé: từ chối.
7. Guest check-out khớp biển: tính phí Guest.
8. Guest check-out bằng biển Monthly: `MANUAL_REVIEW`, không áp dụng vé tháng.
9. Đổi cả thẻ và biển: cảnh báo khi thuộc tính/ảnh tổng quan không khớp.
10. Nhân viên duyệt thủ công: có audit log và phí vẫn thuộc session ban đầu.
11. User thường gọi API approve: trả `403`.
12. Upload Cloudinary thành công nhưng database lỗi: ảnh đã upload được cleanup.

## 10. Kiểm thử Frontend

1. Hai input ảnh hoạt động độc lập.
2. Không thể gửi khi thiếu ảnh.
3. Preview ảnh đúng nhãn, không bị đảo vị trí.
4. Multipart sử dụng đúng `licensePlateImage` và `overviewImage`.
5. `MANUAL_REVIEW` không phát lệnh mở barrier.
6. Màn hình review hiển thị đủ bốn ảnh vào/ra.
7. User không đủ role không thấy nút duyệt.
8. Double click không tạo hai phiên hoặc hai thanh toán.
9. Lỗi OCR cho phép chụp lại ảnh biển số mà không làm mất ảnh tổng quan.
10. Sau khi thanh toán online, barrier chỉ mở khi backend xác nhận thanh toán và xác minh xe đều hợp lệ.

## 11. Thứ tự triển khai

1. Thay đổi model/enum ảnh và migration database.
2. Thêm service lưu evidence và metadata OCR.
3. Tạo endpoint multipart check-in.
4. Tạo endpoint multipart check-out.
5. Thêm trạng thái và endpoint manual review.
6. Sửa Frontend thành hai input ảnh riêng.
7. Chặn mở barrier dựa trên response OCR độc lập.
8. Viết test Backend và Frontend.
9. Chạy thử với các kịch bản Guest, Monthly, đổi biển và đổi cả thẻ.
10. Sau khi luồng mới ổn định mới ngừng sử dụng endpoint upload ảnh chung trong nghiệp vụ cổng vào/ra.
