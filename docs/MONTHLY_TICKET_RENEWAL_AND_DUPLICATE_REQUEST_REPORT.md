# Báo cáo chỉnh sửa logic thẻ tháng: gia hạn và chống đăng ký trùng

## 1. Mục tiêu

Chỉnh sửa nghiệp vụ thẻ tháng để đáp ứng hai yêu cầu:

1. Người dùng có thể gia hạn vé tháng đang sở hữu.
2. Khi người dùng đã có yêu cầu đang chờ thanh toán hoặc đang chờ xét duyệt, người dùng không được tạo thêm yêu cầu đăng ký gói hoặc yêu cầu gia hạn khác.

Rule đề xuất áp dụng ở cấp **người dùng**, không chỉ theo từng xe. Như vậy một tài khoản chỉ có tối đa một yêu cầu thẻ tháng chưa kết thúc tại một thời điểm.

## 2. Hiện trạng trong source code

### 2.1. Đăng ký gói mới

`MonthlyTicketRequestService.submitRequest()` hiện kiểm tra:

- người dùng hiện tại;
- quyền sở hữu phương tiện;
- phương tiện chưa bị xóa;
- gói còn hoạt động;
- loại phương tiện phù hợp với gói.

Tuy nhiên, phương thức này chưa kiểm tra người dùng đã có yêu cầu `PENDING_PAYMENT` hoặc `PENDING_APPROVAL`. Vì vậy người dùng có thể gửi nhiều yêu cầu liên tiếp.

### 2.2. Tạo yêu cầu gia hạn

`MonthlyTicketRenewalService.createRenewalRequest()` đã kiểm tra:

- người dùng sở hữu vé;
- gói gia hạn phải là gói hiện tại;
- gói còn hoạt động;
- loại xe phù hợp.

Nhưng còn thiếu:

- kiểm tra yêu cầu đang mở của người dùng;
- kiểm tra vé có trạng thái hợp lệ để gia hạn;
- kiểm tra chi nhánh được gửi lên có đúng chi nhánh của thẻ/vé hiện tại;
- kiểm tra một vé đã có yêu cầu gia hạn đang xử lý hay chưa;
- khóa dữ liệu khi tạo yêu cầu để tránh hai request đồng thời vượt qua kiểm tra.

### 2.3. Duyệt gia hạn

Trong `MonthlyTicketRequestService.updateStatus()`, khi duyệt yêu cầu gia hạn, hệ thống đang:

```java
oldTicket.setStatus(MonthlyTicketStatus.INACTIVE);
startDate = now;

if (oldTicket.getEndDate().isAfter(now)) {
    endDate = oldTicket.getEndDate().plusMonths(1);
}
```

Cách xử lý này làm vé cũ bị vô hiệu hóa ngay khi quản lý duyệt. Vé mới bắt đầu tại `now`, dù vé cũ vẫn còn thời hạn. Tổng ngày hết hạn có thể vẫn được cộng thêm, nhưng lịch sử kỳ hạn bị chồng lấn và việc tra cứu vé theo thời gian trở nên khó hiểu.

## 3. Trạng thái yêu cầu được xem là chưa kết thúc

Hai trạng thái phải chặn tạo yêu cầu mới:

| Trạng thái | Ý nghĩa | Cho phép tạo yêu cầu khác |
|---|---|---:|
| `PENDING_PAYMENT` | Đã tạo yêu cầu, chưa thanh toán | Không |
| `PENDING_APPROVAL` | Đã thanh toán, đang xét duyệt | Không |
| `APPROVED` | Đã duyệt và cấp vé | Có, nhưng đăng ký mới còn phụ thuộc vé hiện hữu |
| `REJECTED` | Bị từ chối | Có |
| `EXPIRED` | Yêu cầu hết hạn | Có |

Mặc dù yêu cầu người dùng nhấn mạnh trạng thái “đang xét duyệt”, nên chặn cả `PENDING_PAYMENT`. Nếu không, người dùng có thể tạo nhiều yêu cầu nhưng không thanh toán, gây dữ liệu rác và nhiều giao dịch chờ.

## 4. Chỉnh sửa repository

### 4.1. Thêm truy vấn kiểm tra yêu cầu đang mở theo người dùng

Trong `MonthlyTicketRequestRepository`:

```java
boolean existsByUserUserIdAndStatusIn(
        Long userId,
        Collection<MonthlyTicketRequestStatus> statuses
);
```

Cần import:

```java
import java.util.Collection;
import Parking.enums.MonthlyTicketRequestStatus;
```

Nếu Spring Data gặp vấn đề với enum dùng converter, sử dụng JPQL rõ ràng:

```java
@Query("""
    SELECT CASE WHEN COUNT(request) > 0 THEN true ELSE false END
    FROM MonthlyTicketRequest request
    WHERE request.user.userId = :userId
      AND request.status IN :statuses
""")
boolean existsOpenRequestByUser(
        @Param("userId") Long userId,
        @Param("statuses") Collection<MonthlyTicketRequestStatus> statuses
);
```

### 4.2. Kiểm tra yêu cầu gia hạn đang mở theo vé

```java
boolean existsByRenewalOfTicketTicketIdAndStatusIn(
        Long ticketId,
        Collection<MonthlyTicketRequestStatus> statuses
);
```

Kiểm tra theo người dùng đáp ứng rule nghiệp vụ chính. Kiểm tra theo `ticketId` là lớp bảo vệ bổ sung và giúp thông báo lỗi chính xác hơn.

### 4.3. Khóa người dùng khi tạo yêu cầu

Chỉ gọi `exists...` rồi `save()` chưa chống được hai HTTP request đến đồng thời. Nên thêm phương thức lấy user với khóa ghi trong `UserRepository`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT user FROM User user WHERE user.userId = :userId")
Optional<User> findByIdForUpdate(@Param("userId") Long userId);
```

Cả đăng ký mới và gia hạn phải chạy trong `@Transactional`, khóa user trước, sau đó mới kiểm tra yêu cầu đang mở và tạo request.

Nếu database hỗ trợ tốt, có thể bổ sung cơ chế constraint riêng. Tuy nhiên unique constraint thông thường khó biểu diễn điều kiện chỉ unique với một nhóm trạng thái, vì vậy khóa transaction vẫn là giải pháp dễ kiểm soát trong thiết kế hiện tại.

## 5. Chỉnh sửa đăng ký gói mới

Trong `MonthlyTicketRequestService`, tạo hằng số trạng thái đang mở:

```java
private static final List<MonthlyTicketRequestStatus> OPEN_REQUEST_STATUSES = List.of(
        MonthlyTicketRequestStatus.PENDING_PAYMENT,
        MonthlyTicketRequestStatus.PENDING_APPROVAL
);
```

Đánh dấu `submitRequest()` bằng `@Transactional` và kiểm tra trước khi tạo `MonthlyTicketRequest`:

```java
@Transactional
public MonthlyTicketRequest submitRequest(SubmitMonthlyTicketRequest req) {
    User authenticatedUser = currentUserService.getCurrentUser();
    if (authenticatedUser == null) {
        throw new ResourceNotFoundException("User not found");
    }

    User user = userRepo.findByIdForUpdate(authenticatedUser.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    if (requestRepo.existsByUserUserIdAndStatusIn(
            user.getUserId(), OPEN_REQUEST_STATUSES)) {
        throw new InvalidTicketStateException(
                "Bạn đang có yêu cầu thẻ tháng chưa hoàn tất. "
                + "Vui lòng thanh toán hoặc chờ quản lý xét duyệt."
        );
    }

    // Các bước kiểm tra vehicle, policy và branch hiện có.
    // Sau đó mới tạo request PENDING_PAYMENT.
}
```

`MonthlyTicketRequestService` cần inject thêm `UserRepository`.

### 5.1. Kiểm tra vé đang hoạt động khi đăng ký mới

Ngoài yêu cầu đang xét duyệt, không nên cho đăng ký mới nếu xe đã có vé còn hiệu lực. Người dùng phải dùng chức năng gia hạn:

```java
if (monthlyTicketRepo.existsActiveTicketByVehicle(
        vehicle.getVehiclesId(), LocalDateTime.now())) {
    throw new InvalidTicketStateException(
            "Phương tiện đã có vé tháng còn hiệu lực. Vui lòng sử dụng chức năng gia hạn."
    );
}
```

Repository hiện chưa có đúng chữ ký này thì bổ sung truy vấn dựa trên:

```text
vehicleId khớp
status = ACTIVE
startDate <= now
endDate >= now
```

## 6. Chỉnh sửa tạo yêu cầu gia hạn

### 6.1. Chặn yêu cầu trùng

Trong `MonthlyTicketRenewalService.createRenewalRequest()`, sau khi khóa user:

```java
if (requestRepo.existsByUserUserIdAndStatusIn(
        currentUser.getUserId(), OPEN_REQUEST_STATUSES)) {
    throw new InvalidTicketStateException(
            "Bạn đang có yêu cầu thẻ tháng chưa hoàn tất. "
            + "Không thể tạo thêm yêu cầu gia hạn."
    );
}

if (requestRepo.existsByRenewalOfTicketTicketIdAndStatusIn(
        ticketId, OPEN_REQUEST_STATUSES)) {
    throw new InvalidTicketStateException(
            "Vé tháng này đã có yêu cầu gia hạn đang xử lý."
    );
}
```

### 6.2. Kiểm tra trạng thái và dữ liệu vé

Thêm các kiểm tra:

```java
if (ticket.getStatus() != MonthlyTicketStatus.ACTIVE) {
    throw new InvalidTicketStateException(
            "Chỉ có thể gia hạn vé tháng đang hoạt động."
    );
}

if (ticket.getEndDate() == null) {
    throw new InvalidTicketStateException("Vé tháng không có ngày hết hạn hợp lệ.");
}

if (ticket.getParkingCard() == null
        || ticket.getParkingCard().getParkingBranch() == null) {
    throw new InvalidTicketStateException("Vé tháng không có thông tin chi nhánh hợp lệ.");
}
```

Nếu cho phép gia hạn vé vừa hết hạn, dùng khoảng ân hạn rõ ràng, ví dụ 30 ngày:

```java
LocalDateTime now = LocalDateTime.now();
if (ticket.getEndDate().isBefore(now.minusDays(30))) {
    throw new InvalidTicketStateException(
            "Vé đã hết hạn quá thời gian cho phép gia hạn. Vui lòng đăng ký gói mới."
    );
}
```

Khi áp dụng khoảng ân hạn, không được bắt buộc `status == ACTIVE`; thay vào đó chấp nhận `ACTIVE` hoặc `INACTIVE` nếu `endDate` còn trong 30 ngày gần nhất.

### 6.3. Không cho đổi chi nhánh trong luồng gia hạn

Gia hạn sử dụng lại thẻ cũ, do đó chi nhánh phải giữ nguyên:

```java
Long currentBranchId = ticket.getParkingCard()
        .getParkingBranch()
        .getParkingBranchId();

if (!currentBranchId.equals(dto.getBranchId())) {
    throw new InvalidTicketStateException(
            "Gia hạn phải thực hiện tại chi nhánh của vé hiện tại."
    );
}
```

Tốt hơn nữa, bỏ `branchId` khỏi `CreateRenewalRequest` và tự lấy branch từ vé. Frontend khi đó chỉ cần gửi:

```json
{
  "policyId": 10
}
```

Nếu nghiệp vụ không cho đổi gói khi gia hạn, cũng có thể bỏ cả `policyId` và lấy trực tiếp `ticket.getPricePolicy()`. Điều này giảm khả năng frontend gửi dữ liệu sai.

## 7. Chỉnh sửa logic duyệt gia hạn

Có hai thiết kế hợp lệ. Nên chọn một và áp dụng nhất quán.

### Phương án A — cập nhật trực tiếp vé hiện tại (khuyến nghị)

Khi duyệt gia hạn, không tạo vé mới và không vô hiệu hóa vé cũ. Chỉ nối `endDate`:

```java
MonthlyTicket oldTicket = req.getRenewalOfTicket();
LocalDateTime now = LocalDateTime.now();
LocalDateTime baseDate = oldTicket.getEndDate().isAfter(now)
        ? oldTicket.getEndDate()
        : now;

oldTicket.setEndDate(baseDate.plusMonths(1));
oldTicket.setStatus(MonthlyTicketStatus.ACTIVE);
oldTicket.setPricePolicy(req.getPricePolicy());
monthlyTicketRepo.save(oldTicket);
```

Ưu điểm:

- giữ nguyên `ticketId` và `parkingCard`;
- không tạo hai bản ghi thời gian chồng lấn;
- check-in luôn tìm được đúng vé hiện tại;
- logic đơn giản.

Nhược điểm: lịch sử từng lần gia hạn nằm ở `MonthlyTicketRequest` và `Payment`, không nằm thành nhiều bản ghi `MonthlyTicket`.

Đây là phương án phù hợp với model hiện tại vì yêu cầu gia hạn đã có `renewalOfTicket`, payment và thời gian tạo.

### Phương án B — tạo vé mới cho từng kỳ

Nếu cần lưu từng kỳ dưới dạng một `MonthlyTicket`, vé mới phải bắt đầu sau kỳ cũ:

```java
LocalDateTime startDate = oldTicket.getEndDate().isAfter(now)
        ? oldTicket.getEndDate()
        : now;
LocalDateTime endDate = startDate.plusMonths(1);
```

Không được vô hiệu hóa vé cũ ngay nếu `startDate` nằm trong tương lai. Cần job cập nhật trạng thái theo thời gian hoặc truy vấn vé hiệu lực dựa chủ yếu vào `startDate/endDate`.

Phương án này phức tạp hơn vì cùng một thẻ có thể có một vé hiện tại và một vé tương lai. Các truy vấn chống overlap hiện có cũng phải cho phép hai kỳ tiếp giáp nhưng không chồng lấn.

## 8. Thời hạn gia hạn phải theo gói

Code hiện tại luôn dùng `plusMonths(1)`. Nếu hệ thống có gói 3 tháng, 6 tháng hoặc 12 tháng thì đây là lỗi.

Nên thêm trường thời lượng vào `PricePolicy`, ví dụ:

```java
@Column(name = "duration_months")
private Integer durationMonths;
```

Sau đó:

```java
int durationMonths = policy.getDurationMonths();
if (durationMonths <= 0) {
    throw new InvalidTicketStateException("Thời hạn gói không hợp lệ.");
}

oldTicket.setEndDate(baseDate.plusMonths(durationMonths));
```

Nếu mỗi policy hiện chỉ đại diện cho đúng một tháng, vẫn nên đặt hằng số có tên thay vì rải `plusMonths(1)` trong service.

## 9. Luồng nghiệp vụ sau khi sửa

### 9.1. Đăng ký mới

```text
User chọn xe và gói
  -> khóa user
  -> kiểm tra không có request PENDING_PAYMENT/PENDING_APPROVAL
  -> kiểm tra xe chưa có vé còn hiệu lực
  -> tạo request PENDING_PAYMENT
  -> thanh toán thành công
  -> request PENDING_APPROVAL
  -> quản lý duyệt
  -> cấp MonthlyTicket ACTIVE
  -> request APPROVED
```

### 9.2. Gia hạn

```text
User chọn vé hiện tại
  -> khóa user
  -> kiểm tra quyền sở hữu
  -> kiểm tra không có request đang mở
  -> kiểm tra vé/gói/chi nhánh hợp lệ
  -> tạo renewal request PENDING_PAYMENT
  -> thanh toán thành công
  -> request PENDING_APPROVAL
  -> quản lý duyệt
  -> cộng thời hạn vào endDate của vé hiện tại
  -> request APPROVED
```

## 10. API và phản hồi frontend

Khi backend từ chối tạo yêu cầu trùng, nên trả HTTP `409 Conflict` cùng mã lỗi ổn định:

```json
{
  "status": 409,
  "code": "MONTHLY_TICKET_REQUEST_ALREADY_OPEN",
  "message": "Bạn đang có yêu cầu thẻ tháng chưa hoàn tất."
}
```

Frontend cần:

- vô hiệu hóa nút “Đăng ký gói” và “Gia hạn” khi API danh sách yêu cầu có `PENDING_PAYMENT` hoặc `PENDING_APPROVAL`;
- vẫn giữ kiểm tra bắt buộc ở backend;
- nếu `PENDING_PAYMENT`, hiển thị nút tiếp tục thanh toán;
- nếu `PENDING_APPROVAL`, hiển thị “Đang chờ quản lý xét duyệt”;
- sau `REJECTED` hoặc `EXPIRED`, cho phép tạo yêu cầu mới;
- khi xe đã có vé hoạt động, hiển thị nút “Gia hạn” thay cho “Đăng ký mới”.

Không chỉ dựa vào trạng thái đã tải ở frontend vì người dùng có thể mở nhiều tab hoặc gọi API trực tiếp.

## 11. Xử lý yêu cầu chờ thanh toán bị bỏ dở

Nếu chặn `PENDING_PAYMENT` nhưng không có cơ chế hết hạn, người dùng có thể bị khóa vĩnh viễn sau khi bỏ thanh toán.

Cần chuyển request sang `EXPIRED` khi:

- payment đã quá `paymentExpiresAt`; hoặc
- người dùng chủ động hủy yêu cầu trước khi thanh toán.

Có thể thực hiện trước mỗi lần kiểm tra request đang mở:

```text
Tìm PENDING_PAYMENT đã hết hạn
  -> Payment PENDING thành FAILED/EXPIRED theo enum được chọn
  -> MonthlyTicketRequest thành EXPIRED
  -> sau đó mới kiểm tra còn request đang mở hay không
```

Nên có thêm scheduled job để dọn các yêu cầu hết hạn ngay cả khi người dùng không quay lại.

## 12. Kiểm thử bắt buộc

### Đăng ký mới

1. Không có yêu cầu đang mở: tạo request thành công.
2. Có `PENDING_PAYMENT`: trả `409`, không tạo request mới.
3. Có `PENDING_APPROVAL`: trả `409`, không tạo request mới.
4. Request cũ `REJECTED`: được đăng ký lại.
5. Request cũ `EXPIRED`: được đăng ký lại.
6. Xe có vé còn hiệu lực: yêu cầu dùng chức năng gia hạn.
7. Hai request đồng thời: chỉ một request được tạo.

### Gia hạn

1. Chủ vé, đúng gói và không có request mở: tạo renewal request thành công.
2. Người khác gia hạn vé: bị từ chối.
3. Đổi gói hoặc loại xe không phù hợp: bị từ chối.
4. Chọn chi nhánh khác vé hiện tại: bị từ chối.
5. Vé đã có renewal đang xử lý: không tạo thêm.
6. User có request đăng ký mới đang xử lý: không tạo renewal.
7. Thanh toán thành công nhưng chưa duyệt: không cộng hạn.
8. Duyệt thành công khi vé còn hạn: cộng thời lượng từ `oldEndDate`.
9. Duyệt thành công khi vé đã hết hạn: cộng thời lượng từ `now`.
10. Callback/IPN lặp lại: không cộng hạn hai lần.

### Tính idempotent khi duyệt

`updateStatus(APPROVED)` phải tiếp tục yêu cầu trạng thái hiện tại là `PENDING_APPROVAL`. Khi request đã `APPROVED`, gọi duyệt lần nữa phải trả lỗi và không cộng thêm thời hạn.

## 13. Thứ tự triển khai đề xuất

1. Thêm repository query kiểm tra request đang mở.
2. Thêm transaction và khóa user cho đăng ký/gia hạn.
3. Chặn request trùng trong cả hai service.
4. Chặn đăng ký mới khi xe đã có vé hiệu lực.
5. Sửa duyệt gia hạn theo phương án cập nhật `endDate` của vé hiện tại.
6. Thêm cơ chế hết hạn cho `PENDING_PAYMENT`.
7. Chuẩn hóa lỗi `409` cho frontend.
8. Cập nhật giao diện và viết test đồng thời/idempotent.

## 14. Kết quả mong đợi

- Một user chỉ có tối đa một yêu cầu thẻ tháng đang mở.
- User đang chờ xét duyệt không thể đăng ký hoặc gia hạn thêm.
- User có vé hiện tại sử dụng luồng gia hạn, không đăng ký vé mới chồng lấn.
- Gia hạn nối tiếp từ ngày hết hạn hiện tại, không làm mất thời gian còn lại.
- Duyệt hoặc callback lặp không cộng hạn nhiều lần.
- Yêu cầu thanh toán bỏ dở tự hết hạn để không khóa tài khoản vĩnh viễn.
