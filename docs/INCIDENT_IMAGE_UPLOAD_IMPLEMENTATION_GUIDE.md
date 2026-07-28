# Hướng dẫn triển khai upload ảnh riêng cho báo cáo sự cố

## 1. Kết quả kiểm tra hiện trạng

Dự án đã có các thành phần:

- Entity `IncidentImage` ánh xạ bảng `incident_images`.
- Quan hệ `IncidentReport.incidentImages`.
- `IncidentImageRepository`.
- `IncidentReportResponse.images`.
- `CreateIncidentRequest.images` chứa `imageUrl` và `publicId`.
- `IncidentReportService.createReport()` duyệt `request.getImages()` và tạo `IncidentImage`.

Như vậy bảng `incident_images` đã được nối vào luồng tạo và đọc báo cáo, nhưng chưa có luồng upload file hoàn chỉnh:

1. `IncidentReportController` chưa có endpoint multipart upload ảnh sự cố.
2. Frontend phải tự cung cấp `imageUrl` và `publicId`.
3. Backend đang tin URL/public ID do client gửi.
4. Chưa có quy tắc kiểm tra quyền khi thêm ảnh vào một sự cố đã tồn tại.
5. Chưa có quy tắc giới hạn số ảnh, kích thước, định dạng và trạng thái sự cố.
6. Chưa có cleanup Cloudinary khi upload thành công nhưng lưu database thất bại.
7. Chưa có endpoint xóa ảnh sự cố.

## 2. Phạm vi độc lập với ảnh phương tiện

Chức năng này phải độc lập hoàn toàn với hướng dẫn:

`docs/VEHICLE_IMAGE_AND_PLATE_SWAP_PREVENTION_GUIDE.md`

Không dùng chung entity/table:

| Ảnh sự cố | Ảnh phương tiện |
|---|---|
| Entity `IncidentImage` | Entity `VehicleImage` |
| Table `incident_images` | Table ảnh phương tiện hiện có |
| Gắn bằng `incident_id` | Gắn bằng `parking_session_id` |
| Folder `vinparking/incidents/{incidentId}` | Folder `vinparking/parking-sessions/{sessionId}` |
| Dùng làm bằng chứng sự cố | Dùng cho check-in/check-out và xác minh xe |
| Không tự gửi OCR | Chỉ ảnh có purpose `LICENSE_PLATE` mới gửi OCR |

Không thêm `IncidentImage` vào `VehicleImageType`, không thêm ảnh sự cố vào API evidence của parking session và không gửi ảnh sự cố sang Plate Recognizer.

Một báo cáo sự cố có thể tham chiếu `parkingSessionId`, nhưng ảnh của nó vẫn phải lưu trong `incident_images`. Quan hệ với session chỉ cung cấp ngữ cảnh, không biến ảnh sự cố thành `VehicleImage`.

## 3. Luồng nghiệp vụ đề xuất

Sử dụng luồng hai bước:

1. Frontend tạo báo cáo bằng `POST /api/incidents`.
2. Backend trả về `incidentId`.
3. Frontend upload file bằng `POST /api/incidents/{incidentId}/images`.
4. Backend kiểm tra quyền, upload Cloudinary, tạo các dòng `incident_images`.
5. Frontend lấy lại chi tiết sự cố hoặc cập nhật danh sách ảnh từ response upload.

Lợi ích:

- luôn có `incidentId` trước khi tạo folder và bản ghi ảnh;
- không cần URL upload tạm;
- dễ kiểm tra quyền;
- tránh tin `imageUrl/publicId` từ client;
- cho phép bổ sung ảnh trong quá trình xử lý sự cố.

Nếu upload ảnh thất bại, báo cáo vẫn tồn tại. Frontend hiển thị lỗi và cho phép upload lại, không tạo một báo cáo trùng.

## 4. Thay đổi Backend

### 4.1. API upload ảnh

Thêm vào `IncidentReportController`:

```java
@PostMapping(
    value = "/{id}/images",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
)
@Operation(summary = "Upload ảnh bằng chứng cho báo cáo sự cố")
@PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
public ResponseEntity<List<IncidentImageResponse>> uploadIncidentImages(
        @PathVariable Long id,
        @RequestPart("files") List<MultipartFile> files
) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(incidentImageService.uploadImages(id, files));
}
```

Nên tạo `IncidentImageResponse` thành DTO độc lập để dùng lại cho upload, đọc và xóa:

```java
@Getter
@Builder
public class IncidentImageResponse {
    private Long incidentImageId;
    private Long incidentId;
    private String imageUrl;
    private LocalDateTime uploadedAt;
    private Long uploadedById;
    private String uploadedByName;
}
```

Không trả `publicId` cho frontend thông thường vì đây là định danh dùng để quản lý/xóa tài nguyên Cloudinary ở backend.

### 4.2. Service riêng

Tạo:

```text
src/main/java/Parking/Service/IncidentImageService.java
```

Các dependency:

```java
private final IncidentReportRepository incidentReportRepository;
private final IncidentImageRepository incidentImageRepository;
private final CurrentUserService currentUserService;
private final BranchScopeService branchScopeService;
private final Cloudinary cloudinary;
```

Method chính:

```java
@Transactional
public List<IncidentImageResponse> uploadImages(
        Long incidentId,
        List<MultipartFile> files
) {
    validateFiles(files);

    User currentUser = currentUserService.getCurrentUser();
    IncidentReport incident = incidentReportRepository.findById(incidentId)
            .orElseThrow(() ->
                    new ParkingSessionException("Không tìm thấy báo cáo sự cố"));

    assertCanUpload(incident, currentUser);
    assertIncidentAllowsMoreImages(incident, files.size());

    List<String> uploadedPublicIds = new ArrayList<>();

    try {
        List<IncidentImage> images = new ArrayList<>();

        for (MultipartFile file : files) {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder",
                            "vinparking/incidents/" + incidentId,
                            "resource_type", "image",
                            "unique_filename", true,
                            "overwrite", false
                    )
            );

            String imageUrl = result.get("secure_url").toString();
            String publicId = result.get("public_id").toString();
            uploadedPublicIds.add(publicId);

            IncidentImage image = new IncidentImage();
            image.setImageUrl(imageUrl);
            image.setPublicId(publicId);
            image.setUploadedAt(LocalDateTime.now());
            image.setUploadedBy(currentUser);
            image.setIncidentReport(incident);
            images.add(image);
        }

        return incidentImageRepository.saveAllAndFlush(images)
                .stream()
                .map(this::toResponse)
                .toList();
    } catch (IOException ex) {
        uploadedPublicIds.forEach(this::deleteFromCloudinarySilently);
        throw new ParkingSessionException("Không thể upload ảnh sự cố");
    } catch (RuntimeException ex) {
        uploadedPublicIds.forEach(this::deleteFromCloudinarySilently);
        throw ex;
    }
}
```

Phải kiểm tra `secure_url` và `public_id` khác `null` trước khi gọi `toString()`.

### 4.3. Validation file

Quy tắc đề xuất:

- ít nhất 1 file;
- tối đa 5 file mỗi request;
- tối đa 10 ảnh cho một sự cố;
- mỗi file tối đa 10 MB;
- chỉ nhận `image/jpeg`, `image/png`, `image/webp`;
- không chỉ tin `Content-Type`, nên kiểm tra magic bytes bằng Apache Tika hoặc thư viện tương đương;
- tên file không được dùng trực tiếp làm `publicId`;
- file rỗng bị từ chối.

Ví dụ:

```java
private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
        throw new ParkingSessionException("Ảnh sự cố không được để trống");
    }

    if (file.getSize() > MAX_IMAGE_SIZE) {
        throw new ParkingSessionException(
                "Mỗi ảnh sự cố không được vượt quá 10 MB");
    }

    Set<String> allowed = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    if (!allowed.contains(file.getContentType())) {
        throw new ParkingSessionException(
                "Ảnh sự cố phải là JPG, PNG hoặc WEBP");
    }
}
```

### 4.4. Quyền upload

Không được cho mọi user đã đăng nhập upload ảnh vào mọi sự cố.

Quy tắc:

- `USER`: chỉ upload vào sự cố do chính họ báo cáo.
- `STAFF`: upload nếu được phân công hoặc sự cố thuộc chi nhánh của họ.
- `MANAGER`: upload nếu sự cố thuộc chi nhánh họ quản lý.
- `ADMIN`: upload mọi sự cố.
- Sự cố `CANCELLED`: không cho upload.
- Sự cố `RESOLVED`: mặc định không cho upload; nếu nghiệp vụ cần ảnh hậu kiểm thì chỉ `STAFF/MANAGER/ADMIN` được bổ sung và phải ghi log.

Pseudo-code:

```java
private void assertCanUpload(IncidentReport incident, User user) {
    switch (user.getUserRole()) {
        case USER -> {
            if (!incident.getReporter().getUserId()
                    .equals(user.getUserId())) {
                throw new AccessDeniedException(
                        "Bạn không có quyền thêm ảnh vào sự cố này");
            }
        }
        case STAFF -> {
            branchScopeService.assertSameBranch(
                    incident.getParkingBranch().getParkingBranchId());
        }
        case MANAGER -> {
            branchScopeService.assertSameBranch(
                    incident.getParkingBranch().getParkingBranchId());
        }
        case ADMIN -> {
            // Cho phép.
        }
    }
}
```

Phải xử lý trường hợp `parkingBranch == null` theo đúng quy tắc hiện tại của incident; không gọi `getParkingBranchId()` trên `null`.

### 4.5. Repository

Mở rộng `IncidentImageRepository`:

```java
List<IncidentImage>
findByIncidentReportIncidentIdOrderByUploadedAtAsc(Long incidentId);

long countByIncidentReportIncidentId(Long incidentId);

Optional<IncidentImage>
findByIncidentImageIdAndIncidentReportIncidentId(
        Long imageId,
        Long incidentId
);
```

Query theo cả `imageId` và `incidentId` giúp tránh xóa/đọc nhầm ảnh thuộc sự cố khác.

### 4.6. API lấy danh sách ảnh riêng

Chi tiết incident đã trả `images`, nhưng endpoint riêng hữu ích khi frontend muốn refresh gallery:

```http
GET /api/incidents/{incidentId}/images
```

Controller:

```java
@GetMapping("/{id}/images")
@PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
public ResponseEntity<List<IncidentImageResponse>> getIncidentImages(
        @PathVariable Long id
) {
    return ResponseEntity.ok(
            incidentImageService.getImages(id)
    );
}
```

Quyền xem ảnh phải giống quyền xem chi tiết sự cố, không chỉ dựa vào việc user đã đăng nhập.

### 4.7. API xóa ảnh

Đề xuất:

```http
DELETE /api/incidents/{incidentId}/images/{imageId}
```

Quy tắc:

- người báo cáo chỉ được xóa ảnh do mình upload khi incident còn `PENDING`;
- staff/manager được xóa theo branch và chính sách nghiệp vụ;
- admin được xóa;
- lưu audit log trước khi xóa;
- xóa Cloudinary bằng `publicId`;
- chỉ xóa row database sau khi Cloudinary xử lý thành công hoặc dùng trạng thái pending cleanup/retry.

Không nhận `publicId` từ request xóa. Backend phải lấy `publicId` từ `IncidentImage` trong database.

### 4.8. Điều chỉnh `CreateIncidentRequest`

Sau khi có endpoint upload riêng, bỏ:

```java
private List<ImageDto> images;
```

và class:

```java
CreateIncidentRequest.ImageDto
```

Lý do: client không nên tạo quan hệ ảnh bằng URL/public ID tùy ý.

Trong giai đoạn chuyển tiếp:

1. Giữ field nhưng đánh dấu deprecated.
2. Backend bỏ qua hoặc chỉ cho phép dữ liệu được tạo từ upload-token do backend cấp.
3. Frontend chuyển hoàn toàn sang endpoint multipart.
4. Sau khi không còn client cũ, xóa field và đoạn tạo ảnh trong `createReport()`.

Phương án đơn giản, an toàn nhất là tạo incident trước rồi upload file; không cần upload-token tạm.

### 4.9. Audit log

Khi thêm/xóa ảnh nên ghi `IncidentLog`:

```text
actionType = UPLOAD_IMAGE
note = "Đã thêm 3 ảnh bằng chứng"
changedBy
changedAt
```

Khi xóa:

```text
actionType = UPLOAD_IMAGE
note = "Đã xóa ảnh bằng chứng #123"
```

Hệ thống hiện dùng chung `UPLOAD_IMAGE` cho thay đổi liên quan tới ảnh để tương
thích với CHECK constraint của `incident_logs.action_type`; nội dung
`description` phân biệt thao tác thêm hay xóa. Nếu sau này muốn tách thành
`IMAGE_ADDED` và `IMAGE_REMOVED`, phải migration CHECK constraint trong database
trước khi triển khai enum Java mới.

Không lưu URL chứa thông tin nhạy cảm vào `note` nếu log được hiển thị rộng rãi.

## 5. Thay đổi Frontend

### 5.1. Luồng tạo sự cố

```ts
const incident = await api.post("/api/incidents", incidentData);

if (selectedImages.length > 0) {
  const formData = new FormData();

  selectedImages.forEach((file) => {
    formData.append("files", file);
  });

  await api.post(
    `/api/incidents/${incident.data.incidentId}/images`,
    formData
  );
}
```

Không gửi:

```json
{
  "images": [
    {
      "imageUrl": "...",
      "publicId": "..."
    }
  ]
}
```

trong request tạo sự cố mới.

### 5.2. Trạng thái UI

Frontend nên hiển thị riêng:

- `Đang tạo báo cáo`;
- `Đã tạo báo cáo`;
- `Đang upload ảnh 2/4`;
- `Upload ảnh thất bại – Thử lại`;
- `Hoàn tất`.

Nếu tạo report thành công nhưng upload ảnh thất bại:

- không gọi lại API tạo report;
- giữ `incidentId`;
- cho phép retry đúng endpoint upload ảnh;
- thông báo rõ báo cáo đã được tạo nhưng ảnh chưa upload đủ.

### 5.3. Validation frontend

- tối đa 5 ảnh mỗi lần upload;
- tổng số ảnh không vượt quá giới hạn backend;
- JPG, PNG hoặc WEBP;
- tối đa 10 MB mỗi ảnh;
- preview trước khi upload;
- có thể bỏ từng ảnh trước khi gửi;
- không nén ảnh tới mức mất bằng chứng quan trọng;
- loại bỏ metadata GPS phía client nếu chính sách riêng tư yêu cầu, hoặc xử lý ở backend/Cloudinary.

Backend vẫn phải validation lại toàn bộ.

### 5.4. Gallery ảnh sự cố

Màn hình chi tiết incident:

- gọi `GET /api/incidents/{id}` hoặc endpoint images riêng;
- hiển thị thumbnail;
- bấm để xem ảnh lớn;
- hiển thị thời gian và người upload nếu user có quyền;
- chỉ hiện nút xóa theo permission trả từ backend.

Frontend không tự quyết quyền chỉ bằng role. Response có thể bổ sung:

```json
{
  "permissions": {
    "canUploadImages": true,
    "canDeleteImages": false
  }
}
```

Backend vẫn là nơi thực thi quyền cuối cùng.

## 6. Không gửi ảnh sự cố sang AI biển số

Endpoint upload ảnh sự cố không gọi:

```java
LicensePlateRecognitionService
```

Ảnh sự cố có thể là:

- hỏng barrier;
- mất thẻ;
- va chạm;
- cháy nổ;
- mất tài sản;
- khu vực bãi xe;
- phương tiện bị hư hại.

Do đó không thể giả định mọi ảnh sự cố đều là ảnh biển số. Nếu một nghiệp vụ sự cố cụ thể sau này cần OCR biển số, phải có action rõ ràng do người dùng chọn hoặc một endpoint riêng; không tự gửi tất cả ảnh sự cố sang dịch vụ AI.

## 7. Mã lỗi đề xuất

| HTTP | Code | Ý nghĩa |
|---|---|---|
| 400 | `EMPTY_INCIDENT_IMAGE` | File rỗng |
| 400 | `INVALID_INCIDENT_IMAGE_TYPE` | Sai định dạng |
| 400 | `INCIDENT_IMAGE_TOO_LARGE` | Vượt dung lượng |
| 400 | `TOO_MANY_INCIDENT_IMAGES` | Vượt số lượng |
| 403 | `INCIDENT_IMAGE_ACCESS_DENIED` | Không có quyền |
| 404 | `INCIDENT_NOT_FOUND` | Không tìm thấy sự cố |
| 404 | `INCIDENT_IMAGE_NOT_FOUND` | Không tìm thấy ảnh trong sự cố |
| 409 | `INCIDENT_CLOSED_FOR_IMAGES` | Trạng thái sự cố không cho thêm ảnh |
| 502 | `INCIDENT_IMAGE_UPLOAD_FAILED` | Cloudinary/upload provider lỗi |

## 8. Kiểm thử Backend

1. Reporter upload ảnh vào incident của mình thành công.
2. User không thể upload vào incident của user khác.
3. Staff/manager chỉ upload trong branch được phép.
4. Admin upload được.
5. Không upload vào incident `CANCELLED`.
6. File rỗng, sai MIME, quá dung lượng bị từ chối.
7. Quá giới hạn tổng số ảnh bị từ chối.
8. Cloudinary lỗi: không tạo row database.
9. Database lỗi sau upload: cleanup đúng `publicId`.
10. Client gửi URL/public ID giả trong `CreateIncidentRequest`: không tạo ảnh.
11. Xóa ảnh kiểm tra đồng thời `incidentId + imageId`.
12. User không thể xóa ảnh của incident khác.
13. Response incident trả đúng danh sách ảnh.
14. Upload/xóa ảnh tạo audit log.
15. Mock xác nhận `LicensePlateRecognitionService` không được gọi.

## 9. Kiểm thử Frontend

1. Tạo report trước, upload ảnh sau.
2. Upload thất bại không tạo report lần hai.
3. Retry dùng lại `incidentId`.
4. Validation đúng số lượng, loại và dung lượng.
5. Gallery cập nhật sau upload.
6. Không gửi `imageUrl/publicId` trong JSON tạo report.
7. Không gọi API ảnh phương tiện cho ảnh sự cố.
8. Không gọi API OCR cho ảnh sự cố.
9. Nút upload/xóa tuân theo permission response.
10. Hiển thị đúng lỗi phân quyền và lỗi upload.

## 10. Thứ tự triển khai

1. Tạo `IncidentImageResponse` độc lập.
2. Mở rộng `IncidentImageRepository`.
3. Tạo `IncidentImageService`.
4. Thêm endpoint upload và get images.
5. Bổ sung kiểm tra quyền và trạng thái incident.
6. Bổ sung cleanup Cloudinary và audit log.
7. Chuyển frontend sang luồng tạo report rồi upload file.
8. Ngừng nhận `imageUrl/publicId` từ `CreateIncidentRequest`.
9. Thêm endpoint xóa ảnh nếu nghiệp vụ cần.
10. Chạy test phân quyền, upload lỗi và kiểm tra không gọi OCR.
