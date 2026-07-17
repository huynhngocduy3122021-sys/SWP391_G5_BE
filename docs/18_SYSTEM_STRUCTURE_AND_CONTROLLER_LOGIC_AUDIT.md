# Báo cáo rà soát cấu trúc hệ thống và logic trong Controller

## 1. Mục tiêu và phạm vi

Báo cáo này rà soát cấu trúc mã nguồn trong `src/main/java/Parking`, đặc biệt là toàn bộ thư mục `Controller`, theo kiến trúc 3 lớp mà project đã công bố trong `docs/01_ARCHITECTURE.md`:

```text
Client -> Controller -> Service -> Repository -> Database
                       |              |
                       DTO          Entity
```

Kết luận chính: phần lớn Controller đang làm đúng nhiệm vụ chuyển tiếp HTTP sang Service. Tuy nhiên, `MonthlyTicketRequestController` và `RenewalController` đang chứa phần lớn nghiệp vụ vé tháng và truy cập Repository trực tiếp. Đây là hai vi phạm cấu trúc nghiêm trọng nhất. `PaymentController` không truy cập Repository nhưng đang chứa quá nhiều logic xây dựng kết quả điều hướng. Ngoài ra, một số API còn trả trực tiếp JPA Entity.

## 2. Định nghĩa trách nhiệm từng folder

| Folder/file | Trách nhiệm đúng | Không nên chứa |
|---|---|---|
| `Controller/` | Khai báo endpoint, bind/validate request, nhận thông tin HTTP cần thiết, gọi Service, trả HTTP status/response | Truy vấn Repository, thay đổi Entity, luật nghiệp vụ, transaction nghiệp vụ, tính ngày/giá/trạng thái |
| `Service/` | Điều phối use case, kiểm tra quyền sở hữu và luật nghiệp vụ, quản lý transaction, gọi Repository, map Entity/DTO | Chi tiết HTTP như `ResponseEntity`, header hoặc annotation endpoint |
| `Repository/` | Đọc/ghi dữ liệu bằng Spring Data JPA, query theo nghiệp vụ đã được Service yêu cầu | HTTP response, điều phối use case, tự quyết định luật nghiệp vụ |
| `Model/` | JPA Entity và quan hệ dữ liệu; trạng thái nội tại đơn giản nếu áp dụng rich-domain model | DTO API, truy cập Repository, phụ thuộc Controller |
| `dto/request/` | Hợp đồng đầu vào API, validation bằng Jakarta Validation | JPA annotation, truy vấn dữ liệu, business logic |
| `dto/response/` | Hợp đồng đầu ra ổn định, che giấu Entity và quan hệ JPA | Repository hoặc logic ghi dữ liệu |
| `enums/` | Tập giá trị hữu hạn có tên rõ nghĩa | Magic number rải rác ở Controller/Service |
| `config/` | Cấu hình Spring, Security, OpenAPI, Cloudinary, VNPay, filter/bean | Nghiệp vụ vé xe, thanh toán, người dùng |
| `Util/` | Hàm kỹ thuật thuần, không trạng thái và tái sử dụng được | Use case nghiệp vụ hoặc truy cập Database |
| `exception/` | Exception nghiệp vụ có kiểu và ánh xạ lỗi tập trung sang HTTP | `RuntimeException` chung chung trong Controller |
| `Main.java` | Điểm khởi động Spring Boot | Logic khởi tạo dữ liệu hoặc nghiệp vụ |
| `resources/` | Cấu hình và tài nguyên runtime/static | Java business logic |
| `test/` | Unit/integration test theo layer và use case | Mã runtime của ứng dụng |

Gợi ý chuẩn hóa package Java: đổi dần `Controller`, `Service`, `Repository`, `Model`, `Util` thành chữ thường (`controller`, `service`, `repository`, `model`, `util`). Đây là quy ước Java và giúp tránh lỗi khác biệt hoa/thường khi chạy trên Linux; không phải lỗi nghiệp vụ cần sửa ngay.

## 3. Các lỗi sai layer cần ưu tiên

### CRITICAL-01 — `MonthlyTicketRequestController` đang kiêm Service

File: `src/main/java/Parking/Controller/MonthlyTicketRequestController.java`

Các dấu hiệu:

- Dòng 28-34 inject trực tiếp 7 Repository.
- Dòng 40-49 tự tìm user, vehicle, policy và branch.
- Dòng 51-65 tự kiểm tra quyền sở hữu xe, xe đã xóa, policy active và loại xe phù hợp.
- Dòng 68-76 tự tạo, gán trường và lưu `MonthlyTicketRequest`.
- Dòng 88-92 tự lấy current user và query danh sách yêu cầu.
- Dòng 97 đặt `@Transactional` ở Controller.
- Dòng 99-195 chứa toàn bộ state transition duyệt/từ chối, kiểm tra payment, xử lý gia hạn, vô hiệu hóa vé cũ, tìm/cập nhật thẻ và phát hành vé mới.
- Dòng 141-153 tự tính ngày hiệu lực/hết hạn.

Tác hại:

- Luật phát hành vé chỉ dùng được qua HTTP Controller, khó tái sử dụng và unit test độc lập.
- Controller phụ thuộc mạnh vào schema và JPA Entity.
- Transaction nghiệp vụ đặt ở lớp web; thay đổi transport có thể làm mất tính nhất quán.
- Nhiều thao tác ghi (`oldTicket`, `card`, `newTicket`, `request`) nằm trong một endpoint lớn, tăng nguy cơ lỗi trạng thái và race condition khi hai nhân viên duyệt đồng thời.
- Trả trực tiếp Entity có thể lộ trường nội bộ, gặp lazy-loading hoặc vòng lặp JSON.

Hướng sửa:

1. Tạo `MonthlyTicketRequestService` (hoặc tách thêm `MonthlyTicketIssuanceService`).
2. Chuyển các use case `submitRequest`, `getAllRequests`, `getMyRequests`, `approveRequest`, `rejectRequest` vào Service.
3. Đặt `@Transactional` tại `approveRequest`/`rejectRequest` trong Service.
4. Controller chỉ nhận DTO, gọi một phương thức Service và trả response DTO.
5. Dùng exception có kiểu như `ResourceNotFoundException`, `ForbiddenOperationException`, `InvalidTicketStateException`; để `APIExceptionHandler` đổi sang HTTP status.
6. Thay `Integer status` và các số `-1, 0, 1, 2` bằng enum `MonthlyTicketRequestStatus`.
7. Repository nên có cơ chế lock/conditional update khi duyệt để ngăn phát hành hai lần.

### CRITICAL-02 — `RenewalController` đang chứa toàn bộ nghiệp vụ gia hạn

File: `src/main/java/Parking/Controller/RenewalController.java`

Các dấu hiệu:

- Dòng 22-26 inject trực tiếp 5 Repository và không gọi Service nào.
- Dòng 30 đặt `@Transactional` tại Controller.
- Dòng 35-48 tự xác định current user, tải vé và policy, kiểm tra chủ sở hữu.
- Dòng 50-66 chứa thuật toán tìm ngược policy từ request cũ và tự backfill dữ liệu vé.
- Dòng 69-92 kiểm tra luật “chỉ gia hạn cùng gói”, policy active và vehicle type.
- Dòng 95-104 tự tạo và lưu Entity request gia hạn.

Tác hại:

- Migration/backfill dữ liệu bị trộn vào request online và Controller. Một lời gọi API đọc/gia hạn có thể âm thầm sửa vé cũ.
- Logic gia hạn trùng lặp với logic duyệt trong `MonthlyTicketRequestController`, dễ lệch quy tắc.
- Khó kiểm thử các nhánh dữ liệu lịch sử và khó bảo đảm transaction.

Hướng sửa:

1. Chuyển endpoint thành phương thức mỏng gọi `MonthlyTicketRenewalService.createRenewalRequest(ticketId, dto)`.
2. Gom luật xác định policy hiện tại vào một domain/service method dùng chung.
3. Không backfill ngầm trong Controller; tạo migration/script dữ liệu riêng. Nếu bắt buộc lazy backfill, đặt rõ trong Service, có log và test.
4. Service trả `MonthlyTicketRequestResponse`, không trả Entity.
5. Dùng `CurrentUserService` hiện có thay vì đọc `SecurityContextHolder` và query `UserRepository` lặp lại.

### HIGH-03 — Nghiệp vụ vé tháng bị chia sai theo Controller thay vì use case/service

Files liên quan:

- `MonthlyTicketRequestController.java`
- `RenewalController.java`
- `MonthlyTicketController.java`
- `PaymentService.java`

Luồng đăng ký/gia hạn/thanh toán/duyệt/phát hành cùng thao tác trên `MonthlyTicketRequest`, `Payment`, `MonthlyTicket`, `ParkingCard`, nhưng luật đang phân tán. Ví dụ kiểm tra policy và vehicle type xuất hiện ở cả tạo request, tạo renewal và duyệt request.

Hướng sửa: xác định một application service làm chủ vòng đời request, một issuance service làm chủ thao tác cấp vé/thẻ; các Controller chỉ là adapter HTTP. Các invariant dùng chung phải nằm trong một nơi duy nhất.

### HIGH-04 — Dùng magic number cho trạng thái request và ticket

Vị trí tiêu biểu:

- `MonthlyTicketRequestController.java`: dòng 73, 101-102, 170, 177, 187-194.
- `RenewalController.java`: dòng 100.

Các số `0`, `1`, `2`, `-1` không tự mô tả và comment hiện còn cho thấy `0` có nghĩa nghiệp vụ khác nhau tùy ngữ cảnh. Sai một số có thể bỏ qua payment hoặc phát hành sai trạng thái.

Hướng sửa: tạo `MonthlyTicketRequestStatus` và `MonthlyTicketStatus`, lưu bằng `@Enumerated(EnumType.STRING)` nếu có thể; nếu database đang dùng số, dùng converter tập trung và không để số xuất hiện tại Controller.

### HIGH-05 — Exception và HTTP response đang được quyết định trong business flow

Hai Controller trên vừa `throw new RuntimeException`, vừa tự trả `400/403` dạng `String` hoặc `Map`. Điều này tạo response schema không nhất quán và làm nghiệp vụ phụ thuộc HTTP.

Hướng sửa: Service ném exception có kiểu; `APIExceptionHandler` trả một error DTO thống nhất gồm tối thiểu `code`, `message`, `timestamp`, `path`.

## 4. Các vấn đề mức trung bình

### MEDIUM-01 — `PaymentController` chứa quá nhiều logic dựng redirect URL

File: `src/main/java/Parking/Controller/PaymentController.java`, dòng 49-109.

Việc chọn trả JSON hay HTTP redirect là trách nhiệm presentation và có thể ở Controller. Tuy nhiên, việc chọn route theo payment type, lặp encode toàn bộ params, ghép từng query param và chuẩn hóa base URL đã khiến endpoint khó đọc và khó test. File còn import trùng ở dòng 3-5/8-9 và 23-27.

Hướng sửa: tách `PaymentRedirectUrlBuilder` hoặc private collaborator thuộc lớp web/integration. Controller chỉ gọi callback service, quyết định JSON/redirect, rồi dùng builder tạo `URI`. Không chuyển việc xác thực callback/IPN ra khỏi `PaymentService`.

### MEDIUM-02 — Một số Controller trả trực tiếp JPA Entity

Các Controller có import `Parking.Model` trực tiếp:

- `VehicleTypeController` trả `VehicleType`.
- `PricePolicyController` trả `PricePolicy`.
- `VehicleImageController` dùng `VehicleImageType` trong API.
- Hai Controller vé tháng trả nhiều Entity trực tiếp.

Không phải mọi enum/model trong API đều sai, nhưng JPA Entity không nên là hợp đồng response. Hướng sửa: bổ sung response DTO và map trong Service. Điều này tránh lộ quan hệ, trường audit, proxy Hibernate và tránh API tự thay đổi khi schema đổi.

### MEDIUM-03 — Thiếu validation đầu vào ở hai endpoint request vé

- `MonthlyTicketRequestController.submitRequest` nhận `@RequestBody` nhưng không có `@Valid`.
- `RenewalController.createRenewalRequest` nhận `@RequestBody` nhưng không có `@Valid`.

Hướng sửa: khai báo constraint (`@NotNull`, giá trị hợp lệ) trong request DTO và thêm `@Valid`. Validation định dạng nằm ở Controller/DTO; validation tồn tại/quyền sở hữu vẫn nằm ở Service.

### MEDIUM-04 — Lấy current user bị lặp và đặt sai nơi

`MonthlyTicketRequestController` và `RenewalController` tự đọc `SecurityContextHolder`, rồi tìm lần lượt email/phone. Project đã có `CurrentUserService`; nên dùng service này làm một nguồn duy nhất. Controller cũng có thể nhận `Authentication`/principal và truyền định danh sang Service, nhưng không nên tự query `UserRepository`.

### MEDIUM-05 — Lấy client IP chưa thống nhất

- `MonthlyTicketRequestController.createPayment` chỉ dùng `request.getRemoteAddr()`.
- `ParkingSessionController` tự đọc `X-Forwarded-For`.

Đây là concern của web/infrastructure, không phải business logic. Nên có một `ClientIpResolver` dùng chung và cấu hình trusted proxy; không tin `X-Forwarded-For` từ mọi nguồn vì client có thể giả header.

## 5. Kết quả rà soát các Controller còn lại

Nhóm sau nhìn chung đúng ranh giới vì chỉ bind request, gọi Service và tạo HTTP response:

- `AuthController`
- `BookingController`
- `IncidentReportController`
- `LicensePlateRecognitionController`
- `MonthlyTicketController`
- `ParkingBranchController`
- `ParkingCardController`
- `ParkingFloorController`
- `ParkingZoneController`
- `PricePolicyController` (còn vấn đề response Entity)
- `VehicleController`
- `VehicleImageController` (cần phân biệt enum với Entity trong API)
- `VehicleTypeController` (còn vấn đề response Entity)

`ParkingSessionController` nhìn chung mỏng; hàm lấy IP là concern HTTP hợp lệ trong lớp web, nhưng nên dùng resolver chung vì vấn đề bảo mật/proxy nêu trên. `PaymentController` không vi phạm luồng Repository nhưng nên tách URL builder để giảm độ phức tạp.

## 6. Cấu trúc đích đề xuất

```text
Parking/
├── controller/
│   ├── MonthlyTicketRequestController.java
│   ├── MonthlyTicketRenewalController.java
│   └── PaymentController.java
├── service/
│   ├── MonthlyTicketRequestService.java
│   ├── MonthlyTicketRenewalService.java
│   ├── MonthlyTicketIssuanceService.java
│   └── CurrentUserService.java
├── repository/
├── model/
├── dto/
│   ├── request/
│   └── response/
├── enums/
│   ├── MonthlyTicketRequestStatus.java
│   └── MonthlyTicketStatus.java
├── web/
│   ├── ClientIpResolver.java
│   └── PaymentRedirectUrlBuilder.java
├── config/
├── exception/
└── util/
```

Không bắt buộc phải tạo mọi class ngay. Điểm quan trọng là Controller không được gọi Repository hoặc thay đổi Entity, còn transaction phải bao quanh use case trong Service.

## 7. Thứ tự refactor an toàn

1. Viết characterization test cho các luồng submit, payment, approve, reject và renewal hiện tại.
2. Tạo enum trạng thái nhưng giữ mapping database tương thích.
3. Tạo response DTO/error DTO để khóa hợp đồng API trước khi di chuyển logic.
4. Di chuyển `RenewalController` sang `MonthlyTicketRenewalService` vì phạm vi nhỏ hơn.
5. Di chuyển submit/list của `MonthlyTicketRequestController` sang Service.
6. Di chuyển approve/reject sang transactional service; thêm kiểm tra concurrent approval.
7. Gom các invariant policy/vehicle/ownership vào logic dùng chung.
8. Tách redirect URL builder và client IP resolver.
9. Sau khi test ổn định mới chuẩn hóa tên package chữ thường.

## 8. Tiêu chí hoàn thành

- Không Controller nào import hoặc inject `Parking.Repository.*`.
- Không Controller nào có `@Transactional` cho use case nghiệp vụ.
- Không Controller nào gọi setter/save trên JPA Entity.
- Mọi endpoint mutation nhận request DTO có `@Valid` khi phù hợp.
- Service trả response DTO; API không serialize trực tiếp JPA Entity.
- Không còn magic number trạng thái trong Controller/Service.
- Exception nghiệp vụ được ánh xạ tập trung và response lỗi có cùng schema.
- Luồng duyệt request đảm bảo một request không thể phát hành hai vé khi gọi đồng thời.

## 9. Tổng kết mức độ ưu tiên

| Mức | Vấn đề | Ưu tiên |
|---|---|---|
| Critical | `MonthlyTicketRequestController` kiêm Service/Repository orchestration | Sửa đầu tiên |
| Critical | `RenewalController` kiêm Service và migration/backfill | Sửa đầu tiên |
| High | Luật vé tháng phân tán, trùng lặp | Sửa cùng đợt refactor |
| High | Magic number trạng thái | Sửa trước/đồng thời di chuyển logic |
| High | Exception/response lỗi không thống nhất | Sửa cùng Service layer |
| Medium | Redirect builder trong `PaymentController` | Sửa sau luồng vé tháng |
| Medium | Trả trực tiếp JPA Entity | Chuyển dần theo từng API |
| Medium | Validation, current user và client IP chưa tập trung | Sửa theo từng Controller |
