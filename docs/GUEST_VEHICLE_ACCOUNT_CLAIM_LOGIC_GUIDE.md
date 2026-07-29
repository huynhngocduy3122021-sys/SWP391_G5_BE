# Hướng dẫn sửa logic nhận xe Guest khi khách hàng đăng ký tài khoản

## 1. Hiện trạng và nguyên nhân lỗi

Luồng hiện tại:

1. Khi xe vãng lai vào bãi qua `POST /api/parking-sessions/guest/check-in`, `ParkingSessionService.guestCheckIn()` tìm xe theo biển số.
2. Nếu chưa có, `createGuestVehicle()` tạo một bản ghi `Vehicle` có:
   - `vehicleSource = GUEST`;
   - `user = null`.
3. Sau đó khách hàng tạo tài khoản và gọi `POST /api/vehicles` để đăng ký chính biển số đó.
4. `VehicleService.createVehicle()` đang gọi `existsByLicensePlateIgnoreCase()` và trả lỗi `"Biển số xe đã tồn tại"` ngay khi tìm thấy bản ghi.

Như vậy hệ thống đang coi hai trường hợp sau là một:

- biển số đã thuộc một tài khoản khác;
- biển số chỉ là dữ liệu Guest do nhân viên check-in tạo ra và chưa có chủ.

Đây là nguyên nhân khách hàng không thể nhận lại xe Guest vào tài khoản của mình.

Các vị trí liên quan:

- `src/main/java/Parking/Service/ParkingSessionService.java`
  - `guestCheckIn()`;
  - `createGuestVehicle()`.
- `src/main/java/Parking/Service/VehicleService.java`
  - `createVehicle()`.
- `src/main/java/Parking/Repository/VehicleRepository.java`.
- `src/main/java/Parking/Model/Vehicle.java`.
- `src/main/java/Parking/dto/request/CreateVehicleRequest.java`.

## 2. Quy tắc nghiệp vụ đề xuất

Khi người dùng đăng ký biển số:

| Trạng thái bản ghi theo biển số | Xử lý |
|---|---|
| Chưa tồn tại | Tạo `Vehicle` mới, gắn với người dùng đăng nhập, đặt `vehicleSource = REGISTER` |
| Đã tồn tại, `user == null`, `vehicleSource == GUEST` | Không tạo bản ghi mới; cập nhật chính bản ghi Guest, gắn người dùng và đổi nguồn thành `REGISTER` |
| Đã tồn tại và đã thuộc đúng người dùng hiện tại | Trả lỗi rõ ràng `"Biển số xe đã được đăng ký trong tài khoản của bạn"` hoặc trả idempotent response |
| Đã tồn tại và thuộc người dùng khác | Từ chối với `"Biển số xe đã được đăng ký bởi tài khoản khác"` |
| Đã xóa mềm (`deleted = true`) và chưa có chủ | Có thể khôi phục khi nhận xe: đặt `deleted = false` |
| Xe Guest có loại xe khác loại người dùng gửi lên | Từ chối và yêu cầu xác minh/chỉnh loại xe; không tự đổi vì có thể ảnh hưởng lịch sử gửi xe |

Không được xóa bản ghi Guest rồi tạo bản ghi mới. Các `ParkingSession`, ảnh xe, thanh toán và lịch sử khác đang tham chiếu `vehicle_id`; tái sử dụng cùng bản ghi sẽ giữ nguyên toàn bộ lịch sử.

## 3. Không tin `userId` từ request

API có xác thực nhưng `CreateVehicleRequest` hiện nhận `userId` từ client. Nếu dùng trực tiếp trường này, một người dùng có thể thử gắn xe Guest vào tài khoản khác.

Với endpoint khách hàng tự đăng ký xe, phải lấy chủ xe từ `CurrentUserService.getCurrentUser()`:

```java
User currentUser = currentUserService.getCurrentUser();
```

Nên bỏ `userId` khỏi `CreateVehicleRequest` của khách hàng. Nếu quản trị viên cần tạo xe hộ, tạo endpoint/DTO riêng và kiểm tra role rõ ràng.

## 4. Cách sửa đề xuất

### 4.1. Bổ sung khóa khi tìm theo biển số

Thao tác “tìm rồi nhận xe” phải nằm trong một transaction và khóa bản ghi để tránh hai tài khoản cùng nhận một biển số.

Trong `VehicleRepository` thêm:

```java
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    SELECT v
    FROM Vehicle v
    WHERE UPPER(v.licensePlate) = UPPER(:licensePlate)
    """)
Optional<Vehicle> findByLicensePlateForUpdate(
        @Param("licensePlate") String licensePlate
);
```

Ràng buộc unique `uk_vehicle_license_plate` trong entity/database vẫn phải được giữ lại làm lớp bảo vệ cuối cùng.

### 4.2. Chuẩn hóa biển số thống nhất

`ParkingSessionService` đang xóa khoảng trắng và đổi thành chữ hoa, còn `VehicleService` hiện chỉ `trim()`. Cần dùng chung một hàm chuẩn hóa, tốt nhất tách thành component/helper dùng cho cả hai service:

```java
public String normalize(String value) {
    return value.trim()
            .replaceAll("\\s+", "")
            .toUpperCase(Locale.ROOT);
}
```

Nếu hệ thống muốn xem `30A-123.45` và `30A12345` là cùng biển số thì phải thống nhất thêm việc bỏ `-` và `.`, đồng thời migration dữ liệu cũ trước khi đổi quy tắc. Không nên chỉ sửa một service vì sẽ tiếp tục tạo các trường hợp so sánh không đồng nhất.

### 4.3. Sửa `VehicleService.createVehicle()`

Inject thêm `CurrentUserService`, lấy người dùng từ security context, sau đó “tạo mới hoặc nhận xe Guest”:

```java
@Transactional
public VehicleResponse createVehicle(CreateVehicleRequest request) {
    String licensePlate = licensePlateNormalizer.normalize(request.getLicensePlate());
    User currentUser = currentUserService.getCurrentUser();

    VehicleType requestedType = vehicleTypeRepository
            .findById(request.getVehicleTypeId())
            .orElseThrow(() -> new ParkingSessionException("Không tìm thấy loại xe"));

    Optional<Vehicle> existingOpt =
            vehicleRepository.findByLicensePlateForUpdate(licensePlate);

    if (existingOpt.isPresent()) {
        Vehicle existing = existingOpt.get();

        if (existing.getUser() != null) {
            if (existing.getUser().getUserId().equals(currentUser.getUserId())) {
                throw new ParkingSessionException(
                        "Biển số xe đã được đăng ký trong tài khoản của bạn");
            }
            throw new ParkingSessionException(
                    "Biển số xe đã được đăng ký bởi tài khoản khác");
        }

        if (existing.getVehicleSource() != VehicleSource.GUEST) {
            throw new ParkingSessionException(
                    "Biển số xe đã tồn tại và cần được nhân viên xác minh");
        }

        if (!existing.getVehicleType().getVehicleTypeId()
                .equals(requestedType.getVehicleTypeId())) {
            throw new ParkingSessionException(
                    "Loại phương tiện không khớp với dữ liệu xe đã có");
        }

        existing.setUser(currentUser);
        existing.setVehicleSource(VehicleSource.REGISTER);
        existing.setDeleted(false);

        // Chỉ bổ sung thông tin còn thiếu; không âm thầm ghi đè dữ liệu lịch sử.
        if (existing.getVehicleColor() == null) {
            existing.setVehicleColor(request.getVehicleColor());
        }
        if (existing.getVehicleBrand() == null) {
            existing.setVehicleBrand(request.getVehicleBrand());
        }

        return convertToResponse(vehicleRepository.save(existing));
    }

    Vehicle vehicle = new Vehicle();
    vehicle.setLicensePlate(licensePlate);
    vehicle.setVehicleColor(request.getVehicleColor());
    vehicle.setVehicleBrand(request.getVehicleBrand());
    vehicle.setVehicleType(requestedType);
    vehicle.setUser(currentUser);
    vehicle.setVehicleSource(VehicleSource.REGISTER);

    return convertToResponse(vehicleRepository.save(vehicle));
}
```

Ghi chú: tên `licensePlateNormalizer` trong ví dụ là helper cần tạo; nếu chưa tách helper thì có thể dùng tạm một private method, nhưng cả `VehicleService` và `ParkingSessionService` phải áp dụng cùng quy tắc.

### 4.4. Xử lý lỗi cạnh tranh

Dù đã khóa, trường hợp hai transaction cùng tạo một biển số hoàn toàn mới vẫn có thể va vào unique constraint. Có thể bắt `DataIntegrityViolationException`, đọc lại bản ghi và trả lỗi nghiệp vụ phù hợp. Không trả nguyên lỗi SQL cho frontend.

## 5. Kiểm thử bắt buộc

Nên viết integration test cho `VehicleService` với các ca:

1. Chưa có biển số: tạo xe mới, `user` là current user và nguồn là `REGISTER`.
2. Có xe Guest với `user = null`: nhận đúng bản ghi cũ; `vehicle_id` không đổi.
3. Xe Guest có các `ParkingSession`: sau khi nhận xe, lịch sử vẫn trỏ tới cùng `vehicle_id`.
4. Biển số đã thuộc current user: không tạo bản ghi thứ hai.
5. Biển số đã thuộc user khác: từ chối và không thay đổi chủ xe.
6. Xe Guest khác loại xe: từ chối, không cập nhật.
7. Biển số khác hoa/thường hoặc có khoảng trắng: vẫn tìm đúng bản ghi.
8. Hai user nhận cùng lúc: chỉ một request thành công.
9. Request giả `userId` của người khác: xe vẫn chỉ được gắn với user trong security context.
10. Xe Guest đang có phiên gửi xe `ACTIVE`: vẫn có thể nhận quyền sở hữu vì không làm thay đổi `vehicle_id` hay phiên gửi xe.

## 6. Tiêu chí hoàn thành

- Khách hàng đăng ký được biển số đã từng check-in dưới dạng Guest.
- Không sinh thêm một dòng `vehicles` cho cùng biển số.
- `vehicle_id` và toàn bộ lịch sử gửi xe được giữ nguyên.
- Không thể nhận xe đã thuộc tài khoản khác.
- Chủ xe được lấy từ tài khoản đang đăng nhập, không lấy từ payload.
- Các luồng guest check-in, booking, vé tháng và truy vấn lịch sử vẫn hoạt động.
- Có integration test cho các trường hợp ở mục 5.

