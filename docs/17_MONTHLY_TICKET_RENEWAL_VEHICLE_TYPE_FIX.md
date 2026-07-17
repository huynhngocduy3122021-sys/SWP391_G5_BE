# Phương án sửa lỗi gia hạn sai gói theo loại xe

Ngày kiểm tra: 2026-07-17

## 1. Mô tả lỗi

Người dùng đã mua/gia hạn vé tháng cho một phương tiện nhưng có thể chọn gói giá của loại phương tiện khác.

Ví dụ lỗi:

- Xe đang đăng ký là ô tô (`vehicle.vehicleType = CAR`).
- Request gửi lên lại dùng `policyId` của gói xe máy (`pricePolicy.vehicleType = MOTORBIKE`).
- Backend vẫn tạo yêu cầu, lấy giá của gói xe máy để thanh toán và sau đó cấp vé cho ô tô.

Kết quả mong muốn: một phương tiện chỉ được mua hoặc gia hạn bằng chính sách giá thuộc đúng `vehicleType` của phương tiện đó. Ô tô không được dùng gói xe máy và ngược lại.

## 2. Nguyên nhân trong code hiện tại

Điểm phát sinh chính nằm tại:

`src/main/java/Parking/Controller/MonthlyTicketRequestController.java`

Method `submitRequest(...)` hiện chỉ:

1. Tìm `Vehicle` theo `vehicleId`.
2. Tìm `PricePolicy` theo `policyId`.
3. Tìm `ParkingBranch` theo `branchId`.
4. Lưu cả ba đối tượng vào `MonthlyTicketRequest`.

Code chưa đối chiếu:

```java
vehicle.getVehicleType().getVehicleTypeId()
```

với:

```java
policy.getVehicleType().getVehicleTypeId()
```

Vì vậy client có thể gửi bất kỳ `policyId` tồn tại nào. `PaymentService.createMonthlyTicketPayment(...)` tiếp tục lấy trực tiếp `request.getPricePolicy().getBasePrice()`, nên số tiền sai được đưa vào giao dịch VNPay. Method `updateStatus(...)` cũng chưa kiểm tra lại trước khi cấp vé.

Đây là lỗi validation phía backend; chỉ lọc danh sách gói trên frontend là chưa đủ vì request có thể được gọi trực tiếp hoặc dữ liệu cũ có thể đã sai.

## 3. Các validation liên quan cũng đang thiếu

Ngoài lỗi loại xe, luồng hiện tại nên kiểm tra thêm:

- Xe thuộc đúng người đang gửi yêu cầu; hiện một user có thể gửi `vehicleId` của user khác nếu biết ID.
- `PricePolicy.active == true` tại thời điểm tạo yêu cầu và trước khi thanh toán.
- Policy thực sự là gói vé tháng. Hiện model chưa có trường phân loại policy, code có xu hướng nhận biết gói qua tên; cách này không ổn định.
- Khi gia hạn, thẻ/vé cũ phải thuộc đúng chi nhánh được yêu cầu. Hiện code tái sử dụng thẻ cũ nhưng không đối chiếu chi nhánh.
- Trước khi duyệt phải kiểm tra lại toàn bộ invariant, vì request có thể được tạo từ dữ liệu cũ trước khi bản sửa được triển khai.

## 4. Phương án sửa đề xuất

### 4.1. Bắt buộc kiểm tra loại xe khi tạo yêu cầu

Sau khi load `vehicle` và `policy` trong `submitRequest(...)`, thêm kiểm tra:

```java
Long vehicleTypeId = vehicle.getVehicleType().getVehicleTypeId();
Long policyVehicleTypeId = policy.getVehicleType().getVehicleTypeId();

if (!vehicleTypeId.equals(policyVehicleTypeId)) {
    throw new ParkingSessionException(
        "Gói vé tháng không phù hợp với loại phương tiện đã chọn"
    );
}
```

Không so sánh bằng tên `CAR`, `MOTORBIKE` vì tên có thể đổi. Nên so sánh khóa chính `vehicleTypeId`.

### 4.2. Kiểm tra quyền sở hữu xe

Trong cùng method:

```java
if (vehicle.getUser() == null
        || !vehicle.getUser().getUserId().equals(user.getUserId())) {
    throw new ParkingSessionException(
        "Phương tiện không thuộc tài khoản hiện tại"
    );
}
```

Nếu nghiệp vụ cho phép STAFF tạo hộ khách hàng thì cần tách API/role riêng, không nên bỏ hẳn kiểm tra ownership cho mọi role.

### 4.3. Chỉ cho phép policy đang hoạt động

```java
if (!policy.isActive()) {
    throw new ParkingSessionException("Gói vé tháng đã ngừng áp dụng");
}
```

Nên bổ sung một trường phân loại rõ ràng vào `PricePolicy`, ví dụ enum:

```java
public enum PricePolicyType {
    HOURLY,
    MONTHLY
}
```

Sau đó kiểm tra `policy.getPolicyType() == PricePolicyType.MONTHLY`. Không nên xác định gói tháng bằng `policyName.contains("gói")` hoặc `contains("tháng")`.

### 4.4. Gom validation vào service dùng chung

Không nên để toàn bộ nghiệp vụ trong controller. Đề xuất tạo method trong service vé tháng/yêu cầu vé tháng:

```java
private void validateMonthlyPackage(
        Vehicle vehicle,
        PricePolicy policy,
        User requester
) {
    if (vehicle.getVehicleType() == null
            || policy.getVehicleType() == null
            || !vehicle.getVehicleType().getVehicleTypeId()
                .equals(policy.getVehicleType().getVehicleTypeId())) {
        throw new ParkingSessionException(
            "Gói vé tháng không phù hợp với loại phương tiện đã chọn"
        );
    }

    if (!policy.isActive()) {
        throw new ParkingSessionException("Gói vé tháng đã ngừng áp dụng");
    }

    if (vehicle.getUser() == null
            || !vehicle.getUser().getUserId().equals(requester.getUserId())) {
        throw new ParkingSessionException(
            "Phương tiện không thuộc tài khoản hiện tại"
        );
    }
}
```

Gọi validation này ở ba lớp bảo vệ:

1. Trước khi lưu `MonthlyTicketRequest`.
2. Trước khi tạo `Payment` trong `PaymentService.createMonthlyTicketPayment(...)`.
3. Trước khi cấp vé trong `updateStatus(...)`.

Điểm 2 và 3 là kiểm tra phòng thủ nhằm chặn request cũ đã lưu sai trước ngày triển khai.

### 4.5. Không cho gia hạn bằng cách chọn một gói khác loại

Luồng gia hạn hiện tại thực chất tạo `MonthlyTicketRequest` mới rồi khi duyệt sẽ tìm vé gần nhất theo xe. Khi đã xác định `oldTicket`, cần đảm bảo:

```java
Long currentVehicleTypeId = oldTicket.getVehicle()
        .getVehicleType()
        .getVehicleTypeId();
Long requestedPolicyVehicleTypeId = req.getPricePolicy()
        .getVehicleType()
        .getVehicleTypeId();

if (!currentVehicleTypeId.equals(requestedPolicyVehicleTypeId)) {
    throw new ParkingSessionException(
        "Không thể gia hạn vé ô tô bằng gói của xe máy hoặc ngược lại"
    );
}
```

Thực tế `oldTicket.vehicle` và `req.vehicle` phải là cùng một xe do query tìm theo vehicle ID. Validation quan trọng nhất vẫn là giữa loại xe và loại policy.

### 4.6. Kiểm tra chi nhánh khi tái sử dụng thẻ cũ

Trước khi dùng `oldTicket.getParkingCard()`:

```java
Long oldBranchId = oldTicket.getParkingCard()
        .getParkingBranch()
        .getParkingBranchId();
Long requestedBranchId = req.getParkingBranch().getParkingBranchId();

if (!oldBranchId.equals(requestedBranchId)) {
    throw new ParkingSessionException(
        "Không thể gia hạn bằng thẻ thuộc chi nhánh khác"
    );
}
```

Nếu nghiệp vụ cho phép chuyển chi nhánh thì phải cấp thẻ tháng mới tại chi nhánh mới và giải phóng thẻ cũ, không được âm thầm tái sử dụng thẻ của chi nhánh cũ.

## 5. Cải thiện API lấy danh sách gói cho frontend

API hiện tại `GET /api/price-policies` trả tất cả policy. Frontend dễ hiển thị lẫn gói ô tô và xe máy.

Nên thêm API lọc:

```http
GET /api/price-policies/monthly?vehicleTypeId={vehicleTypeId}
```

Repository gợi ý:

```java
List<PricePolicy> findByVehicleTypeVehicleTypeIdAndActiveTrueAndPolicyType(
    Long vehicleTypeId,
    PricePolicyType policyType
);
```

Luồng frontend:

1. Người dùng chọn xe.
2. Frontend lấy `vehicle.vehicleType.vehicleTypeId`.
3. Frontend gọi API danh sách gói theo đúng `vehicleTypeId`.
4. Khi đổi xe, xóa policy đang chọn và tải lại danh sách.

Frontend chỉ giúp trải nghiệm đúng; backend vẫn phải giữ validation ở mục 4.

## 6. Thứ tự triển khai an toàn

1. Thêm validation loại xe/policy, ownership và active policy khi submit.
2. Thêm cùng validation trước tạo payment và trước approve.
3. Viết test cho các trường hợp đúng/sai.
4. Kiểm tra dữ liệu `monthly_ticket_request` đang pending để tìm request sai loại xe.
5. Sau khi dữ liệu cũ được xử lý, thêm API filter policy cho frontend.
6. Giai đoạn sau thêm `PricePolicyType` và migration database để phân biệt `HOURLY`/`MONTHLY` rõ ràng.

## 7. Test case bắt buộc

### Case 1: Ô tô mua gói ô tô

- Vehicle type: `CAR`.
- Policy vehicle type: `CAR`.
- Kết quả: tạo request thành công.

### Case 2: Ô tô mua/gia hạn gói xe máy

- Vehicle type: `CAR`.
- Policy vehicle type: `MOTORBIKE`.
- Kết quả: HTTP 400, không tạo request, không tạo payment.

### Case 3: Xe máy mua/gia hạn gói ô tô

- Vehicle type: `MOTORBIKE`.
- Policy vehicle type: `CAR`.
- Kết quả: HTTP 400, không tạo request, không tạo payment.

### Case 4: Policy bị khóa sau khi request được tạo

- Request còn pending.
- Policy được chuyển sang `active = false`.
- Kết quả: không cho tạo payment hoặc approve.

### Case 5: Request cũ có policy sai loại xe

- Dữ liệu request đã tồn tại trước bản sửa.
- Kết quả: bước payment/approve phải chặn, không cấp vé.

### Case 6: User dùng vehicle ID của người khác

- Vehicle không thuộc user đăng nhập.
- Kết quả: HTTP 400 hoặc 403, không tạo request.

### Case 7: Gia hạn khác chi nhánh

- Vé/thẻ cũ thuộc branch A, request chọn branch B.
- Kết quả: chặn hoặc thực hiện quy trình chuyển chi nhánh riêng; không tái sử dụng thẻ branch A cho branch B.

## 8. Kiểm tra dữ liệu cũ

Trước khi cho phép duyệt các request pending, chạy truy vấn đối soát tương đương:

```sql
SELECT
    r.id,
    r.vehicle_id,
    v.vehicle_type_id AS vehicle_type_id,
    r.policy_id,
    p.vehicle_type_id AS policy_vehicle_type_id,
    r.status
FROM monthly_ticket_request r
JOIN vehicles v ON v.vehicle_id = r.vehicle_id
JOIN price_policies p ON p.price_policy_id = r.policy_id
WHERE v.vehicle_type_id <> p.vehicle_type_id;
```

Không tự động sửa `policy_id` vì có liên quan đến số tiền. Với request chưa thanh toán nên hủy và tạo lại; với giao dịch đã thanh toán cần đối soát thủ công trước khi hoàn tiền hoặc thu bổ sung.

## 9. Kết luận

Sửa tối thiểu bắt buộc là so sánh `vehicle.vehicleType.vehicleTypeId` với `pricePolicy.vehicleType.vehicleTypeId` ở backend trước khi lưu request. Bản sửa hoàn chỉnh cần lặp lại kiểm tra trước payment và approve để bảo vệ dữ liệu cũ, đồng thời frontend chỉ hiển thị policy đúng loại xe.

Không nên chỉ sửa giao diện vì client có thể gửi trực tiếp một `policyId` không hợp lệ.
