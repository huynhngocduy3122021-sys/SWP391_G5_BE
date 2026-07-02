# Fix lỗi không tải được Parking Card

## Nguyên nhân

DB đang lưu `parking_card.status` dạng số: `0`, `1`, ...

Code đang map enum dạng string:

```java
@Enumerated(EnumType.STRING)
private ParkingCardStatus status;
```

Nên API `GET /api/parking-cards` có thể lỗi khi đọc `0` thành enum.

## Cách sửa khuyến nghị

Giữ code dùng `EnumType.STRING`, sửa dữ liệu DB sang chữ.

```sql
ALTER TABLE parking_card ALTER COLUMN status NVARCHAR(20) NULL;

UPDATE parking_card
SET status =
    CASE status
        WHEN '0' THEN 'AVAILABLE'
        WHEN '1' THEN 'IN_USE'
        WHEN '2' THEN 'LOST'
        WHEN '3' THEN 'DISABLED'
        ELSE status
    END;
```

Nếu cột `type` cũng đang số/null:

```sql
ALTER TABLE parking_card ALTER COLUMN type NVARCHAR(20) NULL;

UPDATE parking_card
SET type =
    CASE type
        WHEN '0' THEN 'REGULAR'
        WHEN '1' THEN 'MONTHLY'
        WHEN '2' THEN 'VIP'
        WHEN NULL THEN 'REGULAR'
        ELSE type
    END;

UPDATE parking_card
SET type = 'REGULAR'
WHERE type IS NULL;
```

## Dữ liệu hợp lệ sau khi sửa

`status`:

* `AVAILABLE`
* `IN_USE`
* `LOST`
* `DISABLED`

`type`:

* `REGULAR`
* `MONTHLY`
* `VIP`

## Không khuyến nghị

 Không nên đổi code về `EnumType.ORDINAL`, vì sau này đổi thứ tự enum sẽ làm sai dữ liệu cũ.

## 5. Bổ sung phân quyền chi nhánh (Branch Scope) cho Manager/Staff

### Vấn đề
Trước đó, API `GET /api/parking-cards` và các API CRUD khác của `ParkingCard` trả về toàn bộ dữ liệu thẻ gửi xe trong hệ thống, chưa áp dụng cơ chế phân quyền dữ liệu theo chi nhánh (`BranchScopeService`). Điều này khiến:
- Tài khoản `MANAGER` / `STAFF` của chi nhánh A có thể xem, tạo, sửa hoặc xóa thẻ gửi xe của chi nhánh B.
- Giao diện của Manager có thể tải lượng dữ liệu không thuộc phạm vi quản lý hoặc gặp lỗi phân quyền ngầm khi liên kết dữ liệu khác.

### Cách xử lý đã triển khai
1. **Repository ([ParkingCardRepository.java](file:///D:/Ki7/SWP391/Index/Index/src/main/java/Parking/Repository/ParkingCardRepository.java))**:
   * Thêm hàm `findByParkingBranchParkingBranchId(Long parkingBranchId)` để lọc thẻ theo chi nhánh.
2. **Service ([ParkingCardService.java](file:///D:/Ki7/SWP391/Index/Index/src/main/java/Parking/Service/ParkingCardService.java))**:
   * Tích hợp `BranchScopeService` để kiểm tra và giải quyết branch scope của Manager/Staff.
   * `getAllParkingCards(Long branchId)`: Lọc danh sách thẻ dựa trên branch của tài khoản Manager/Staff.
   * `getParkingCardById`, `updateParkingCard`, `deleteParkingCard`: Đảm bảo Manager/Staff chỉ có thể xem/sửa/xóa thẻ thuộc chi nhánh của mình.
   * `createParkingCard`: Xác thực rằng chi nhánh gán cho thẻ mới phải thuộc quyền quản lý của Manager/Staff.
3. **Controller ([ParkingCardController.java](file:///D:/Ki7/SWP391/Index/Index/src/main/java/Parking/Controller/ParkingCardController.java))**:
   * API `GET` lấy danh sách hỗ trợ tham số lọc `branchId`.
   * Thêm `@PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")` bảo vệ các endpoint.
