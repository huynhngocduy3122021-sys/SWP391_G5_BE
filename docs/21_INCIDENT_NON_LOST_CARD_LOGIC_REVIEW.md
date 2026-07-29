# Review logic báo cáo sự cố ngoài mất thẻ

## 1. Phạm vi và kết luận

Tài liệu này ghi lại kết quả review các loại sự cố sau để sửa sau:

- `TECHNICAL_ERROR`
- `PAYMENT_ERROR`
- `VEHICLE_DAMAGE`
- `SECURITY_INCIDENT`
- `POWER_OUTAGE`
- `BARRIER_ERROR`
- `OTHER`

Phần CRUD, phân trang và audit log đã có cấu trúc tương đối rõ. Tuy nhiên, logic hiện tại chưa nên đưa vào production vì còn lỗi phân quyền theo session/chi nhánh, chuyển trạng thái không hợp lệ và thiếu validation theo từng loại sự cố.

## 2. Danh sách vấn đề cần sửa

### P0 - Kiểm tra quyền khi gắn `parkingSessionId`

**Vị trí:** `IncidentReportService.createReport()`, đoạn tìm session từ `request.getParkingSessionId()`.

Hiện service chỉ kiểm tra session có tồn tại và lấy branch từ session, nhưng không kiểm tra người tạo có quyền đối với session đó.

Rủi ro:

- USER có thể đoán ID và tạo incident gắn với session của người khác.
- STAFF/MANAGER có thể tạo incident gắn với session ở chi nhánh khác.
- Response incident có thể làm lộ dữ liệu liên quan đến session/thẻ.

Quy tắc đề xuất:

```text
USER    -> session.vehicle.user.id phải bằng currentUser.id
STAFF   -> session.parkingBranch phải trùng chi nhánh của currentUser
MANAGER -> session.parkingBranch phải trùng chi nhánh của currentUser
ADMIN   -> được phép thao tác toàn hệ thống
```

Đối với guest vehicle không có `vehicle.user`, cần xác định rõ USER có được phép gắn session hay chỉ STAFF/MANAGER thực hiện.

### P0 - Không được phân công nhân viên khác chi nhánh

**Vị trí:** `IncidentReportService.assignIncident()`.

Code hiện chỉ xác nhận người nhận có role `STAFF` hoặc `MANAGER`, chưa kiểm tra chi nhánh của người nhận.

Cần kiểm tra thêm:

- `assignedStaff.parkingBranch` không null.
- Chi nhánh người nhận trùng với `report.parkingBranch`.
- Tài khoản không bị xóa hoặc khóa.
- Nếu nghiệp vụ chỉ cho STAFF trực tiếp xử lý thì không nên cho MANAGER làm người nhận.

### P1 - Chặn chuyển trạng thái không hợp lệ

**Vị trí:** `resolveIncident()` và `cancelIncident()`.

Các luồng sai hiện có thể xảy ra:

```text
CANCELLED -> RESOLVED
CANCELLED -> CANCELLED
```

Lần cancel tiếp theo còn ghi đè `cancelledAt`, `cancellationReason` và tạo audit log mới.

State machine đề xuất:

```text
PENDING     -> IN_PROGRESS | CANCELLED
IN_PROGRESS -> RESOLVED | CANCELLED
WAITING_*   -> IN_PROGRESS | RESOLVED | CANCELLED
RESOLVED    -> không được chuyển tiếp
CANCELLED   -> không được chuyển tiếp
```

Cần xác nhận có cho phép `PENDING -> RESOLVED` hay bắt buộc phải assign/chuyển `IN_PROGRESS` trước.

### P1 - Sắp xếp priority/status đang không đúng nghiệp vụ

**Vị trí:** `IncidentReportController.getAllIncidents()`.

Code đang dùng:

```java
Sort.by(
    Sort.Order.desc("priority"),
    Sort.Order.asc("status"),
    Sort.Order.desc("createdAt")
)
```

Vì enum được lưu bằng `EnumType.STRING`, database thường sắp xếp theo chữ cái. `desc("priority")` không đảm bảo thứ tự:

```text
CRITICAL -> HIGH -> MEDIUM -> LOW
```

Hướng sửa:

- Viết query có `CASE WHEN` cho priority và status; hoặc
- Lưu thêm `priorityRank`/`statusRank` dạng số.

### P1 - Bổ sung validation theo từng loại sự cố

Hiện chỉ `TECHNICAL_ERROR` và `BARRIER_ERROR` bắt buộc có `locationDetails`. Các loại còn lại có thể được tạo chỉ với title và description.

Quy tắc tối thiểu đề xuất:

| Incident type | Dữ liệu cần kiểm tra |
|---|---|
| `PAYMENT_ERROR` | `parkingSessionId` hoặc `paymentId` |
| `VEHICLE_DAMAGE` | session/vehicle, vị trí; cân nhắc bắt buộc ảnh |
| `SECURITY_INCIDENT` | vị trí và mô tả chi tiết |
| `POWER_OUTAGE` | branch và khu vực bị ảnh hưởng |
| `TECHNICAL_ERROR` | vị trí và thiết bị liên quan |
| `BARRIER_ERROR` | vị trí hoặc mã barrier |
| `OTHER` | mô tả đủ chi tiết |

Cần quyết định các trường bắt buộc dựa trên UI và nghiệp vụ thực tế trước khi triển khai.

### P1 - Không ghi `lostCardFee` cho incident khác mất thẻ

**Vị trí:** `ResolveIncidentRequest` và `IncidentReportService.resolveIncident()`.

Hiện mọi loại incident đều có thể gửi `lostCardFee`, và service sẽ lưu nếu khác null.

Cần:

- Chỉ chấp nhận `lostCardFee` khi `incidentType == LOST_CARD`.
- Reject request nếu loại khác vẫn gửi trường này.
- Thêm `@DecimalMin("0.00")` để chặn số âm.
- Cân nhắc tách DTO resolve mất thẻ khỏi DTO resolve sự cố chung.

### P2 - Xử lý `priority = null` và quyền đặt CRITICAL

**Vị trí:** `CreateIncidentRequest.priority` và `IncidentReportService.createReport()`.

Giá trị mặc định trong DTO không bảo vệ trường hợp client gửi rõ `"priority": null`. Service hiện có thể gán null vào cột database không nullable.

Hướng sửa:

```java
IncidentPriority priority = request.getPriority() != null
        ? request.getPriority()
        : IncidentPriority.MEDIUM;
```

Cần cân nhắc:

- USER chỉ được tạo với `MEDIUM` mặc định; hoặc
- USER được đề xuất priority nhưng MANAGER là người xác nhận; hoặc
- Chỉ STAFF/MANAGER/ADMIN được đặt `CRITICAL`.

### P2 - Validate ảnh đính kèm

**Vị trí:** `CreateIncidentRequest.ImageDto`.

Chưa có validation cho:

- `imageUrl` null/rỗng hoặc sai định dạng.
- `publicId` null/rỗng.
- Số lượng ảnh tối đa.
- Domain ảnh được phép.

Đề xuất thêm `@Valid` cho list, `@Size` giới hạn số ảnh và validation URL/public ID. Nếu frontend hiển thị URL trực tiếp, chỉ nên chấp nhận domain upload được hệ thống tin cậy.

### P2 - Xem lại việc chặn báo cáo tại branch inactive

**Vị trí:** `IncidentReportService.createReport()` với điều kiện `!branch.isActive()`.

Chi nhánh inactive vẫn có thể cần báo:

- Mất điện.
- Lỗi kỹ thuật.
- Sự cố an ninh.
- Sự cố khiến chi nhánh phải tạm ngừng hoạt động.

Đề xuất không dùng `branch.active` để chặn toàn bộ incident. Có thể chỉ chặn một số luồng vận hành như booking/check-in, còn báo cáo sự cố vẫn được phép.

### P3 - Làm rõ `parkingCardId` đối với incident không phải LOST_CARD

`CreateIncidentRequest` có `parkingCardId`, nhưng service chỉ xử lý trường này trong nhánh `LOST_CARD`. Với các loại khác, client gửi trường này sẽ bị bỏ qua mà không báo lỗi.

Cần chọn một trong hai hướng:

- Loại trường này khỏi request sự cố chung; hoặc
- Cho phép liên kết card ở các loại phù hợp và validate quyền/chi nhánh; hoặc
- Reject rõ ràng nếu client gửi field không áp dụng.

## 3. Những phần hiện làm tốt

- Có audit log cho create, assign, resolve và cancel.
- Endpoint đã có `@PreAuthorize` theo vai trò.
- Danh sách incident của STAFF/MANAGER được scope theo chi nhánh.
- USER chỉ được xem chi tiết incident do chính mình tạo.
- Khi có session, branch được lấy từ session thay vì tin hoàn toàn vào `parkingBranchId` của client.
- Các thao tác thay đổi trạng thái sử dụng transaction.
- Có kiểm tra vị trí cho lỗi kỹ thuật và lỗi barrier.

## 4. Checklist test cần bổ sung

Chưa thấy unit/integration test riêng cho `IncidentReportService` hoặc `IncidentReportController`. Khi sửa nên bổ sung tối thiểu:

- [ ] USER không thể gắn incident vào session của USER khác.
- [ ] USER xử lý đúng trường hợp guest session không có chủ tài khoản.
- [ ] STAFF không thể tạo hoặc thao tác incident ở branch khác.
- [ ] MANAGER không thể assign STAFF/MANAGER ở branch khác.
- [ ] Không assign tài khoản deleted/locked hoặc chưa có branch.
- [ ] Không resolve incident đã `CANCELLED`.
- [ ] Không cancel lại incident đã `CANCELLED`.
- [ ] Không cancel incident đã `RESOLVED`.
- [ ] State transition đúng với state machine đã thống nhất.
- [ ] Danh sách sắp xếp đúng `CRITICAL -> HIGH -> MEDIUM -> LOW`.
- [ ] `priority = null` được mặc định hoặc trả HTTP 400 rõ ràng.
- [ ] USER không tự nâng priority lên `CRITICAL` nếu nghiệp vụ không cho phép.
- [ ] Incident khác `LOST_CARD` không thể lưu `lostCardFee`.
- [ ] `lostCardFee` âm bị từ chối.
- [ ] Mỗi incident type được kiểm tra đúng dữ liệu bắt buộc.
- [ ] URL ảnh rỗng/sai định dạng và list quá số lượng bị từ chối.
- [ ] Xác nhận có thể tạo incident cần thiết khi branch inactive.

## 5. Thứ tự triển khai đề xuất

1. Sửa kiểm tra ownership/branch khi nhận `parkingSessionId`.
2. Sửa kiểm tra branch và trạng thái tài khoản của người được assign.
3. Chuẩn hóa state machine và chặn terminal-state transitions.
4. Sửa thứ tự sort priority/status.
5. Bổ sung validation theo incident type và validation request chung.
6. Bổ sung test service/controller cho toàn bộ các rule trên.

