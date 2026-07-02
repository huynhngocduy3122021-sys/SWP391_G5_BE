# Review chuc nang MonthlyTicket

Ngay kiem tra: 2026-07-02

## Ket luan nhanh

Chuc nang `MonthlyTicket` hien tai **chua co field `parkingBranchId` truc tiep** trong bang `monthly_ticket`, request, response hay controller.

Ve thang **khong phai hoan toan toan he thong**, vi no dang lien ket voi `ParkingCard`, ma `ParkingCard` bat buoc thuoc mot `ParkingBranch`. Noi cach khac, branch cua ve thang hien tai chi duoc **suy ra gian tiep qua the gui xe**:

`MonthlyTicket -> ParkingCard -> ParkingBranch`

Tuy nhien, ve mat API va logic nghiep vu, chuc nang nay **chua chia branch day du** vi chua co filter, response, phan quyen, va validate theo branch.

## Hien trang code

### Controller

File: `src/main/java/Parking/Controller/MonthlyTicketController.java`

Dang co cac API CRUD co ban:

* `POST /api/monthly-tickets`: tao ve thang.
* `GET /api/monthly-tickets`: lay tat ca ve thang.
* `GET /api/monthly-tickets/{id}`: lay theo ID.
* `PUT /api/monthly-tickets/{id}`: cap nhat.
* `DELETE /api/monthly-tickets/{id}`: xoa.

Chua co API nao nhan `branchId`, vi du:

* `GET /api/monthly-tickets?branchId=...`
* `GET /api/monthly-tickets/branch/{branchId}`

### Model

File: `src/main/java/Parking/Model/MonthlyTicket.java`

Bang `monthly_ticket` dang gom:

* `ticket_id`
* `vehicle_id`
* `parking_card_id`
* `guest_name`
* `guest_phone`
* `start_date`
* `end_date`
* `status`

Khong co cot:

* `parking_branch_id`

File: `src/main/java/Parking/Model/ParkingCard.java`

`ParkingCard` co:

* `parking_branch_id`
* quan he `ManyToOne` den `ParkingBranch`

Viec nay lam cho ve thang co the suy ra branch qua the.

### DTO

File:

* `src/main/java/Parking/dto/request/CreateMonthlyTicketRequest.java`
* `src/main/java/Parking/dto/request/UpdateMonthlyTicketRequest.java`
* `src/main/java/Parking/dto/response/MonthlyTicketResponse.java`

Request hien chi nhan:

* `vehicleId`
* `parkingCardId`
* `guestName`
* `guestPhone`
* `startDate`
* `endDate`
* `status`

Response hien chi tra:

* `ticketId`
* `vehicleId`
* `licensePlate`
* `parkingCardId`
* `cardCode`
* `guestName`
* `guestPhone`
* `startDate`
* `endDate`
* `status`

Chua tra ve:

* `parkingBranchId`
* `parkingBranchName`

### Service va Repository

File: `src/main/java/Parking/Service/MonthlyTicketService.java`

Service dang:

* Tim `Vehicle` theo `vehicleId`.
* Tim `ParkingCard` theo `parkingCardId`.
* Luu monthly ticket voi vehicle va card.
* `getAllMonthlyTickets()` goi `monthlyTicketRepository.findAll()`.

Chua co:

* Kiem tra the co thuoc branch cua user dang dang nhap hay khong.
* Kiem tra branch cua the con active hay khong.
* Kiem tra the co dung loai `MONTHLY` hay khong.
* Kiem tra the dang `AVAILABLE` hay hop le de gan cho ve thang hay khong.
* Loc danh sach ve thang theo branch.
* Repository method tim theo `parkingCard.parkingBranch.parkingBranchId`.

File: `src/main/java/Parking/Repository/MonthlyTicketRepository.java`

Hien chi extends `JpaRepository<MonthlyTicket, Long>`, chua co query theo branch.

## Chuc nang con thieu

### 1. Branch scope cho monthly ticket

Nen them `BranchScopeService` vao `MonthlyTicketService`.

Ly do: cac module khac nhu `BookingService` va `ParkingSessionService` da dung branch scope de:

* ADMIN xem duoc tat ca chi nhanh.
* MANAGER/STAFF chi xem va thao tac trong chi nhanh cua minh.

MonthlyTicket hien chua dung co che nay nen MANAGER/STAFF co nguy co xem/sua/xoa ve thang cua branch khac neu biet ID.

### 2. Loc danh sach theo branch

Nen them repository:

```java
List<MonthlyTicket> findByParkingCardParkingBranchParkingBranchId(Long parkingBranchId);
```

Hoac query linh hoat:

```java
@Query("""
    SELECT mt FROM MonthlyTicket mt
    WHERE :branchId IS NULL
       OR mt.parkingCard.parkingBranch.parkingBranchId = :branchId
""")
List<MonthlyTicket> findAllByBranchId(@Param("branchId") Long branchId);
```

Sau do `getAllMonthlyTickets()` nen lay branch theo:

```java
Long branchId = branchScopeService.resolveReadableBranchId(null);
```

### 3. Tra branch trong response

Nen them vao `MonthlyTicketResponse`:

* `parkingBranchId`
* `parkingBranchName`

Nguon du lieu:

```java
ParkingBranch branch = monthlyTicket.getParkingCard().getParkingBranch();
```

Viec nay giup frontend biet ro ve thang thuoc chi nhanh nao.

### 4. Validate branch khi tao/cap nhat

Khi tao hoac doi `parkingCardId`, nen kiem tra:

* `parkingCard.getParkingBranch()` khong null.
* Branch cua card dang active.
* User hien tai co quyen thao tac tren branch do.
* Neu request co them `parkingBranchId`, card phai thuoc dung branch request.

Neu khong them `parkingBranchId` vao request, van co the lay branch tu card va validate bang `BranchScopeService.assertSameBranch(...)`.

### 5. Validate loai va trang thai the

Viec monthly ticket lien ket voi `ParkingCard` nhung hien chua kiem tra:

* The co type `MONTHLY` hay khong.
* The co dang bi DISABLED/LOST/IN_USE hay khong.
* Mot the co dang gan voi ve thang active khac hay khong.

Nen them rule:

* Chi cho phep card type `MONTHLY` dung cho ve thang.
* Khong cho tao ve thang active neu card da co ve thang active khac.
* Can can nhac cap nhat status cua card khi gan/huy ve thang.

### 6. Validate trung lap ve thang theo xe

Hien chua thay rule ngan mot xe co nhieu ve thang active cung luc.

Nen them repository check:

```java
boolean existsByVehicleVehiclesIdAndStatus(Long vehicleId, Integer status);
```

Hoac tot hon la check khoang thoi gian giao nhau:

```java
boolean existsByVehicleVehiclesIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
    Long vehicleId,
    Integer status,
    LocalDateTime endDate,
    LocalDateTime startDate
);
```

### 7. Validate thoi gian

Hien chua thay check:

* `startDate` phai truoc `endDate`.
* `endDate` khong nen nam trong qua khu khi tao ve active.
* Khi het han nen co scheduler/cap nhat status tu dong, hoac tinh expired dua tren `endDate`.

### 8. Tich hop check-in/check-out voi ve thang

`ParkingSessionService` hien dang xu ly check-in/check-out nhu luot gui xe thong thuong.

Chua thay logic:

* Neu card la monthly card va co monthly ticket active thi mien/giam phi.
* Kiem tra bien so xe check-in co khop xe dang ky ve thang.
* Kiem tra ve thang con han.
* Kiem tra ve thang thuoc dung branch cua card.

Neu nghiep vu yeu cau ve thang dung de ra vao bai, can bo sung logic nay trong check-in/check-out hoac payment.

## Nen chon huong nao?

### Huong A: Ve thang theo branch thong qua ParkingCard

Day la huong it thay doi database nhat.

Giu model `MonthlyTicket` hien tai, khong them `parking_branch_id`. Branch cua ve thang duoc xac dinh bang:

`monthlyTicket.parkingCard.parkingBranch`

Can bo sung:

* Response tra `parkingBranchId`, `parkingBranchName`.
* Repository query theo `parkingCard.parkingBranch`.
* Service validate branch scope.
* Controller ho tro filter branch neu can.

### Huong B: Them `parking_branch_id` vao MonthlyTicket

Huong nay ro rang hon ve mat database, nhung co nguy co trung lap du lieu voi `ParkingCard.parkingBranch`.

Neu chon huong nay, can validate:

* `monthly_ticket.parking_branch_id` phai trung voi `parking_card.parking_branch_id`.
* Khi doi card/branch phai dong bo lai.

Chi nen chon huong B neu nghiep vu cho phep ve thang co branch doc lap voi the, hoac ve thang co the ton tai truoc khi cap the vat ly.

## De xuat

Nen chon **Huong A**: ve thang thuoc branch thong qua `ParkingCard`.

Ly do:

* Code hien tai da co `ParkingCard.parkingBranch`.
* Moi the gui xe da duoc gan chi nhanh.
* It can thay doi schema.
* Phu hop voi cach `ParkingSessionService` dang suy ra branch tu card khi check-in.

Danh sach viec nen lam tiep:

1. Them query `findAllByBranchId` vao `MonthlyTicketRepository`.
2. Inject `BranchScopeService` vao `MonthlyTicketService`.
3. Loc `getAllMonthlyTickets()` theo branch cua user.
4. Kiem tra quyen branch khi get/update/delete theo ID.
5. Validate branch active va card branch khi create/update.
6. Them `parkingBranchId`, `parkingBranchName` vao `MonthlyTicketResponse`.
7. Them validate card type `MONTHLY`, card status, trung card active, trung xe active.
8. Them logic ve thang vao check-in/check-out/payment neu ve thang duoc dung de mien/giam phi.

