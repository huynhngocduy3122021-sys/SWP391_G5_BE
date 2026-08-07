# Phương án sửa lỗi tái sử dụng thẻ tháng vẫn nhận xe cũ

## 1. Hiện tượng

Sau khi dừng vé tháng của xe A, nhân viên đưa cùng một thẻ vật lý cho xe B. Khi check-in, hệ thống vẫn nhận thẻ thuộc xe A hoặc báo biển số xe B không khớp với thông tin đăng ký cũ.

## 2. Nguyên nhân trong code hiện tại

Hệ thống có hai trạng thái độc lập:

- `parking_card.status`: trạng thái sử dụng của thẻ vật lý (`AVAILABLE`, `IN_USE`, `LOST`, ...).
- `monthly_ticket.status`: trạng thái quyền sử dụng vé tháng (`ACTIVE`, `INACTIVE`).

Việc đổi `parking_card.status` thành `AVAILABLE` không làm vé tháng cũ ngừng hoạt động. `ParkingCardService.updateParkingCard()` hiện cho phép đổi trạng thái thẻ trực tiếp nhưng không cập nhật `MonthlyTicket` đang liên kết với thẻ.

Khi check-in, `ParkingSessionService` tìm vé theo `parking_card_id`, trạng thái vé và thời hạn:

```java
MonthlyTicket activeTicket = monthlyTicketRepository
        .findActiveTicketByCard(parkingCard.getParkingCardId(), currentTime)
        .orElseThrow(...);
```

Sau đó xe được kiểm tra theo `activeTicket.getVehicle()`. Vì vậy nếu vé của xe A vẫn `ACTIVE` và còn hạn thì thẻ vẫn được coi là thuộc xe A, dù thẻ vật lý đã được đưa về `AVAILABLE`.

Ngoài ra, `findActiveTicketByCard()` hiện lấy danh sách rồi gọi `findFirst()` mà không có `ORDER BY`. Nếu dữ liệu đã có hai vé `ACTIVE` cùng thẻ và cùng khoảng thời gian, bản ghi được chọn là không xác định. Đây là lý do lỗi có thể xuất hiện không ổn định.

## 3. Giải pháp đề xuất

### 3.1. Tạo nghiệp vụ dừng vé tháng riêng

Không dùng API cập nhật `ParkingCard` để dừng thuê bao. Thêm hàm `stopMonthlyTicket()` trong `MonthlyTicketService` và một endpoint riêng, ví dụ:

```http
PATCH /api/monthly-tickets/{ticketId}/stop
```

Hàm phải chạy trong cùng một transaction:

```java
@Transactional
public void stopMonthlyTicket(Long ticketId) {
    MonthlyTicket ticket = findMonthlyTicket(ticketId);
    ParkingCard card = ticket.getParkingCard();

    branchScopeService.assertSameBranch(
            card.getParkingBranch().getParkingBranchId());

    if (parkingSessionRepository
            .existsByParkingCardParkingCardIdAndStatus(
                    card.getParkingCardId(), ParkingSessionStatus.ACTIVE)) {
        throw new InvalidTicketStateException(
                "Không thể dừng vé khi thẻ đang có phiên gửi xe hoạt động");
    }

    ticket.setStatus(MonthlyTicketStatus.INACTIVE);
    monthlyTicketRepository.save(ticket);

    if (card.getStatus() != ParkingCardStatus.LOST
            && card.getStatus() != ParkingCardStatus.DISABLED) {
        card.setStatus(ParkingCardStatus.AVAILABLE);
        parkingCardRepository.save(card);
    }
}
```

Không đổi `ticket.vehicle` và không xóa liên kết lịch sử của vé cũ. Khi cấp cho xe B, hệ thống tạo một `MonthlyTicket` mới liên kết cùng thẻ vật lý.

### 3.2. Không cho sửa trạng thái thẻ để thay thế nghiệp vụ dừng vé

Trong `ParkingCardService.updateParkingCard()`, trước khi cho chuyển một thẻ tháng sang `AVAILABLE`, cần kiểm tra:

- Thẻ không có `ParkingSession` đang `ACTIVE`.
- Thẻ không còn `MonthlyTicket` đang `ACTIVE` và còn hiệu lực.

Nếu còn vé active thì trả lỗi và yêu cầu gọi endpoint dừng vé. Điều này tránh tình trạng giao diện hiển thị thẻ khả dụng nhưng quyền sử dụng cũ vẫn còn.

### 3.3. Bảo đảm mỗi thẻ chỉ có tối đa một vé đang hiệu lực

Luồng duyệt yêu cầu mới đã gọi `existsActiveTicketByCard()`, nhưng cần thực hiện kiểm tra và lưu trong transaction có khóa bản ghi thẻ để tránh hai quản lý duyệt đồng thời.

Thêm vào `ParkingCardRepository`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT card FROM ParkingCard card WHERE card.parkingCardId = :id")
Optional<ParkingCard> findByIdForUpdate(@Param("id") Long id);
```

Trong `MonthlyTicketRequestService.updateStatusInternal()`, dùng `findByIdForUpdate()` thay cho `findByParkingCardId()` trước khi kiểm tra `existsActiveTicketByCard()` và tạo vé mới.

Các API tạo/cập nhật vé trực tiếp trong `MonthlyTicketService` cũng phải áp dụng cùng quy tắc. Không nên chỉ kiểm tra khi request gửi `status = 1`; trạng thái cuối cùng của entity mới là giá trị cần kiểm tra.

### 3.4. Không dùng `findFirst()` để che dữ liệu trùng

Thay `findActiveTicketByCard()` bằng truy vấn trả về toàn bộ kết quả và kiểm tra số lượng:

```java
List<MonthlyTicket> tickets = monthlyTicketRepository
        .findActiveTicketsByCard(cardId, currentTime);

if (tickets.isEmpty()) {
    throw new ParkingSessionException(
            "Thẻ tháng không có vé tháng hoạt động hoặc đã hết hạn");
}
if (tickets.size() > 1) {
    throw new InvalidTicketStateException(
            "Dữ liệu không hợp lệ: thẻ có nhiều vé tháng đang hoạt động");
}

MonthlyTicket activeTicket = tickets.get(0);
```

Cách này không tự sửa dữ liệu sai nhưng ngăn hệ thống âm thầm chọn nhầm xe.

## 4. Dữ liệu cũ cần làm sạch

Tìm các thẻ có nhiều vé đang active và còn hạn:

```sql
SELECT parking_card_id, COUNT(*) AS active_ticket_count
FROM monthly_ticket
WHERE status = 1
  AND start_date <= GETDATE()
  AND end_date >= GETDATE()
GROUP BY parking_card_id
HAVING COUNT(*) > 1;
```

Xem chi tiết các vé liên quan:

```sql
SELECT ticket_id, parking_card_id, vehicle_id,
       status, start_date, end_date
FROM monthly_ticket
WHERE parking_card_id = :parkingCardId
ORDER BY created_at DESC, ticket_id DESC;
```

Với mỗi thẻ bị trùng, xác định vé hiện tại đúng theo nghiệp vụ; chuyển các vé cũ sang `INACTIVE`. Không xóa vé cũ vì vé có thể được tham chiếu bởi lịch sử yêu cầu gia hạn và thanh toán.

```sql
UPDATE monthly_ticket
SET status = 0
WHERE ticket_id IN (:oldTicketIds);
```

Chỉ chạy câu lệnh cập nhật sau khi đã đối chiếu đúng `ticket_id`, xe và lịch sử giao thẻ.

## 5. Các file cần sửa khi triển khai

1. `MonthlyTicketController.java`: thêm endpoint dừng vé.
2. `MonthlyTicketService.java`: thêm `stopMonthlyTicket()`, inject `ParkingSessionRepository`, và thống nhất kiểm tra thẻ active trong create/update.
3. `ParkingCardService.java`: không cho đưa thẻ tháng về `AVAILABLE` nếu vé hoặc phiên gửi xe vẫn active.
4. `ParkingCardRepository.java`: thêm truy vấn khóa thẻ `findByIdForUpdate()`.
5. `MonthlyTicketRequestService.java`: khóa thẻ trước khi duyệt và cấp vé mới.
6. `ParkingSessionService.java`: phát hiện nhiều vé active thay vì lấy `findFirst()`.
7. `MonthlyTicketRepository.java`: bổ sung truy vấn đếm/tìm vé active phù hợp nếu cần.

## 6. Kịch bản kiểm thử bắt buộc

1. Cấp thẻ M01 cho xe A; xe A check-in thành công.
2. Xe A đang ở trong bãi: gọi dừng vé phải bị từ chối.
3. Sau khi xe A checkout: dừng vé; vé A thành `INACTIVE`, thẻ M01 thành `AVAILABLE`.
4. Dùng M01 cấp vé mới cho xe B; chỉ tạo một vé active cho M01.
5. Xe A dùng M01 phải bị báo sai biển số.
6. Xe B dùng M01 phải check-in thành công và session phải lưu `vehicle_id` của xe B.
7. Hai yêu cầu cùng được duyệt với M01: chỉ một yêu cầu được cấp vé.
8. Nếu cố tình tạo hai vé active trong DB, check-in phải báo lỗi dữ liệu thay vì chọn ngẫu nhiên một xe.

## 7. Tiêu chí hoàn thành

- Một thẻ vật lý chỉ có tối đa một vé tháng đang hiệu lực tại một thời điểm.
- Dừng vé luôn cập nhật `monthly_ticket.status`, không chỉ cập nhật `parking_card.status`.
- Không giải phóng thẻ khi còn phiên gửi xe active.
- Tái sử dụng thẻ tạo vé mới cho xe mới nhưng vẫn giữ lịch sử vé cũ.
- Check-in không còn dùng `findFirst()` để che trường hợp dữ liệu active bị trùng.
