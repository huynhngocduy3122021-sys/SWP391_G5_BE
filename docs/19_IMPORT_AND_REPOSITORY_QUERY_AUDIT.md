# Báo cáo chuẩn hóa import và tối ưu Repository query

## 1. Phạm vi kiểm tra

Đã rà soát mã Java trong:

- `src/main/java/Parking/Controller`
- `src/main/java/Parking/Service`
- `src/main/java/Parking/Repository`
- `src/main/java/Parking/Model`
- `src/main/java/Parking/config`
- `src/main/java/Parking/dto`
- `src/test/java`

Nguyên tắc áp dụng:

- Loại bỏ import không dùng, import trùng và fully-qualified class khi có thể import rõ ràng.
- Không thay `@Query` chỉ vì Spring Data hỗ trợ derived query; chỉ thay khi query mới tương đương và hiệu quả hơn.
- Xóa method Repository khi toàn project không có nơi gọi.
- Không thay đổi query phức tạp liên quan transaction, thời gian, phân quyền chi nhánh hoặc báo cáo nếu chưa có bằng chứng là dư thừa.

## 2. Import đã chuẩn hóa

### `PaymentController`

Đã xóa các import và field không còn sử dụng sau khi logic tạo redirect được chuyển sang `PaymentRedirectUrlBuilder`:

- `java.net.URLEncoder`
- `java.nio.charset.StandardCharsets`
- `org.springframework.beans.factory.annotation.Value`
- field `frontendUrl`

Đã thay tên đầy đủ `Parking.web.PaymentRedirectUrlBuilder` tại field bằng import:

```java
import Parking.web.PaymentRedirectUrlBuilder;
```

### `VehicleTypeRepository`

Đã xóa import không sử dụng:

```java
import java.util.function.LongSupplier;
```

### `ParkingBranchRepository`

Đã thay fully-qualified annotation/class bằng import rõ ràng:

- `LockModeType`
- `Lock`
- `Query`
- `Param`

### `ParkingCardRepository`

Đã import rõ ràng các kiểu được dùng trong query method:

- `LockModeType`
- `Lock`
- `ParkingCardStatus`
- `ParkingCardType`

Đã bỏ việc viết tên package đầy đủ ngay trong annotation, generic return type và tham số.

## 3. Repository query/method đã chỉnh

### 3.1. Bỏ method trùng với `JpaRepository.findById`

Đã xóa:

```java
ParkingBranchRepository.findByParkingBranchId(Long id)
```

Method này chỉ được gọi một lần và có cùng mục đích với `findById` được kế thừa từ `JpaRepository`. `ParkingSessionService` đã chuyển sang:

```java
parkingBranchRepository.findById(branchId)
```

### 3.2. Xóa hai method thẻ xe không được sử dụng

Đã xóa khỏi `ParkingCardRepository`:

```java
findByCardCode(String cardCode)
findByCardCodeAndParkingBranchParkingBranchId(String cardCode, Long branchId)
```

Toàn project không có call site. Luồng hiện tại dùng `findByCardCodeIgnoreCase`, phù hợp hơn vì mã thẻ không nên phụ thuộc chữ hoa/chữ thường.

### 3.3. Không tải toàn bộ thẻ tháng chỉ để lấy phần tử đầu

Trước đây:

```java
findAvailableMonthlyCardsByBranch(branchId).stream().findFirst()
```

Query cũ tải toàn bộ thẻ `AVAILABLE/MONTHLY` của chi nhánh vào memory rồi mới lấy một thẻ. Đã thay bằng derived query có giới hạn:

```java
findFirstByParkingBranchParkingBranchIdAndStatusAndTypeOrderByParkingCardIdAsc(...)
```

Lợi ích:

- Database chỉ cần trả tối đa một record.
- Kết quả có thứ tự ổn định theo `parkingCardId`.
- Thêm `PESSIMISTIC_WRITE` để giảm nguy cơ hai transaction duyệt vé cùng chọn một thẻ khả dụng.

Lưu ý: method này phải được gọi bên trong transaction. `MonthlyTicketRequestService.updateStatus` hiện là transactional nên đáp ứng điều kiện đó.

### 3.4. Không tải toàn bộ lịch sử vé chỉ để lấy vé mới nhất

Trước đây:

```java
findByVehicleVehiclesIdOrderByEndDateDesc(vehicleId).stream().findFirst()
```

Đã thay bằng:

```java
findFirstByVehicleVehiclesIdOrderByEndDateDesc(vehicleId)
```

`MonthlyTicketRequestService` đã gọi trực tiếp method mới. Database giới hạn kết quả thay vì trả toàn bộ lịch sử vé của xe.

## 4. Query được giữ lại và lý do

### Query có pessimistic lock

- `ParkingBranchRepository.findAndLockByParkingBranchId`
- `PaymentRepository.findByTransactionRefForUpdate`
- các query/method lấy parking session có `@Lock`

Các query này không dư thừa. Chúng bảo vệ luồng booking, callback thanh toán hoặc check-in/check-out trước concurrent request.

### Query lọc chi nhánh tùy chọn

- `BookingRepository.findAllByBranchId`
- `ParkingSessionRepository.findAllByBranchId`
- `MonthlyTicketRepository.findAllByBranchId`

Điều kiện `:branchId IS NULL OR ...` cho phép một method phục vụ cả admin và manager theo scope. Có thể tách thành `findAll()` và derived query để dễ tối ưu index, nhưng chưa thay vì phải kiểm chứng behavior và execution plan với dữ liệu thật.

### Query overlap và vé đang hiệu lực

Các query trong `MonthlyTicketRepository` kiểm tra giao nhau của khoảng thời gian, thẻ đang dùng và vé nhân viên đang active được giữ lại. Derived method name không biểu đạt tốt điều kiện interval và optional exclusion `ticketId`.

### Query báo cáo/tổng hợp

- `BookingRepository.countActiveBookings*`
- `ParkingZoneRepository.calculateTotalCapacityByBranch`
- các query filter/paging của incident report

Các query `COUNT`, `SUM` và filter động nên thực thi tại database, không nên thay bằng `findAll()` rồi xử lý bằng Java stream.

### Query tìm request lịch sử đã cấp vé

`MonthlyTicketRequestRepository.findIssuedRequestCandidates` hiện phục vụ dữ liệu lịch sử/backfill policy. Query vẫn tải danh sách rồi lấy phần tử đầu. Có thể tối ưu tiếp bằng `Pageable` hoặc native/derived limit, nhưng chưa thay trong đợt này vì query có điều kiện nullable `issuedAt`, payment status và status converter; cần integration test với database trước khi thay để tránh chọn sai request lịch sử.

## 5. Test được đồng bộ

Sau khi Model đổi trạng thái request từ số sang enum, test cũ vẫn gọi/so sánh `0`, `1`, `2`. Đã cập nhật thành:

- `PENDING_PAYMENT`
- `PENDING_APPROVAL`
- `APPROVED`
- `REJECTED`

`MonthlyTicketRequestControllerTest` cũng đã được cập nhật đúng kiến trúc mới: Controller test mock `MonthlyTicketRequestService` thay vì mock các Repository đã được chuyển khỏi Controller.

## 6. Kết quả xác minh

Đã chạy:

```text
mvn clean test
mvn test
```

Kết quả cuối:

```text
BUILD SUCCESS
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

Main source đã được clean compile: 164 Java source files.

Hai cảnh báo không làm build thất bại và chưa thuộc phạm vi sửa query/import:

- `VnPayConfig` dùng API deprecated.
- `ExternalLicensePlateOcrClient` có unchecked/unsafe operation.

## 7. Các việc nên làm tiếp

1. Bổ sung integration test dùng SQL Server/Testcontainers cho query lock và query thời gian.
2. Dùng `Optional<User>` trong `UserRepository` thay vì trả `null` để thống nhất cách xử lý not-found.
3. Đổi `existsByCardCode` thành `existsByCardCodeIgnoreCase` để đồng nhất với lookup thẻ, sau khi kiểm tra collation database.
4. Dùng `Pageable` cho API trả toàn bộ payment, ticket, session và request để tránh tải không giới hạn.
5. Bổ sung Spotless/Checkstyle hoặc IDE save action để tự động phát hiện import không dùng trong CI.
6. Kiểm tra execution plan cho các query có `:branchId IS NULL OR ...`; nếu index không được dùng hiệu quả, tách thành hai repository method.

## 8. Danh sách file thay đổi trong đợt này

- `src/main/java/Parking/Controller/PaymentController.java`
- `src/main/java/Parking/Repository/VehicleTypeRepository.java`
- `src/main/java/Parking/Repository/ParkingBranchRepository.java`
- `src/main/java/Parking/Repository/ParkingCardRepository.java`
- `src/main/java/Parking/Repository/MonthlyTicketRepository.java`
- `src/main/java/Parking/Service/ParkingSessionService.java`
- `src/main/java/Parking/Service/MonthlyTicketRequestService.java`
- `src/test/java/Parking/Controller/MonthlyTicketRequestControllerTest.java`
- `src/test/java/Parking/Service/PaymentServiceMonthlyTicketTest.java`

