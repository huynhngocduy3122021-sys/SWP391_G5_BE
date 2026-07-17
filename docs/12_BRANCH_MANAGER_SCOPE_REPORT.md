# Bao cao va de xuat enforce du lieu theo ParkingBranch cho Manager/Staff

Ngay cap nhat: 2026-07-01

## 1. Muc tieu

Tai lieu nay danh gia cach backend hien tai phan quyen du lieu theo `ParkingBranch` cho cac tai khoan `MANAGER` va `STAFF`, dong thoi de xuat huong implement toi uu.

Nguyen tac can dat:

- `ADMIN` duoc xem va quan ly du lieu toan he thong.
- `MANAGER` chi xem va thao tac du lieu cua chi nhanh duoc gan.
- `STAFF` chi thao tac nghiep vu trong chi nhanh duoc gan.
- `USER` chi xem du lieu ca nhan, tru cac API public duoc thiet ke rieng.
- Backend phai tu enforce theo current user, khong tin vao `parkingBranchId` frontend gui len.

## 2. Danh gia nhanh

### 2.1. Cach lam hien tai da co nen tang dung

Model `User` da co lien ket toi chi nhanh:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parking_branch_id")
private ParkingBranch parkingBranch;
```

Khi admin tao staff/manager, `UserService` da gan branch:

```java
newStaff.setUserRole(UserRole.STAFF);
newStaff.setParkingBranch(branch);

newManager.setUserRole(UserRole.MANAGER);
newManager.setParkingBranch(branch);
```

Response login/user cung da tra:

```java
private Long parkingBranchId;
private String parkingBranchName;
```

Ket luan: he thong da co du lieu goc de biet manager/staff thuoc branch nao.

### 2.2. Diem chua toi uu

Mot so API/service van doc du lieu toan he thong:

```java
parkingBranchRepository.findAll()
parkingSessionRepository.findAll()
bookingRepository.findAll()
```

Neu controller/service khong loc bang branch cua current user, manager branch A co the xem du lieu branch B.

Trong `SecurityConfig`, nhieu API quan ly dang `permitAll`:

```java
"/api/parking-sessions/**",
"/api/parking-branches/**",
"/api/parking-floors/**",
"/api/parking-zones/**",
"/api/parking-cards/**",
"/api/payments/**"
```

Day la rui ro lon nhat vi:

- API khong bat buoc login.
- Service khong co current user dang tin cay de scope du lieu.
- Role `ADMIN`, `MANAGER`, `STAFF`, `USER` khong duoc chan tu dau vao.

Trong `IncidentReportService.getAllIncidents()` da co comment ve scope branch, nhung chua implement:

```java
if (user.getUserRole() == UserRole.STAFF || user.getUserRole() == UserRole.MANAGER) {
    // Staff/Manager chi duoc loc su co cua chi nhanh cua ho ...
}
```

Sau do code van goi:

```java
incidentReportRepository.findByFilters(branchId, ...)
```

`branchId` van la gia tri frontend gui len, chua bi enforce theo `user.getParkingBranch().getParkingBranchId()`.

## 3. Ket luan hien tai

Cach lam hien tai **chua toi uu va chua an toan** o lop backend.

Frontend co the lay `parkingBranchId` sau login roi gui vao API filter de hien thi dung UI, nhung do chi la loc phia client/API consumer. Backend van phai tu dam bao:

- Manager/staff khong xem duoc branch khac.
- Manager/staff khong tao/sua/xoa/tac dong len du lieu branch khac.
- Neu frontend co tinh gui `branchId` khac, backend phai tu choi bang loi 403/business exception.

## 4. Huong thiet ke toi uu

### 4.1. Tao helper lay current user dung chung

Khong nen moi service tu viet lai `SecurityContextHolder`. Nen tao mot service/helper dung chung, vi cac service hien tai nhu `BookingService`, `IncidentReportService` dang co logic rieng.

De xuat tao:

```text
src/main/java/Parking/Service/CurrentUserService.java
```

Vi du:

```java
@Service
public class CurrentUserService {

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ParkingSessionException("Yeu cau dang nhap");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User user)) {
            throw new ParkingSessionException("Thong tin dang nhap khong hop le");
        }

        return user;
    }
}
```

### 4.2. Tao helper enforce branch scope dung chung

Nen gom rule branch vao mot helper rieng thay vi copy/paste trong tung service.

De xuat tao:

```text
src/main/java/Parking/Service/BranchScopeService.java
```

Core API nen co:

```java
public Long resolveReadableBranchId(Long requestedBranchId) {
    User user = currentUserService.getCurrentUser();

    if (user.getUserRole() == UserRole.ADMIN) {
        return requestedBranchId; // null = xem tat ca
    }

    if (user.getUserRole() == UserRole.MANAGER || user.getUserRole() == UserRole.STAFF) {
        Long userBranchId = requireUserBranchId(user);

        if (requestedBranchId != null && !requestedBranchId.equals(userBranchId)) {
            throw new ParkingSessionException("Ban khong co quyen truy cap chi nhanh nay");
        }

        return userBranchId;
    }

    throw new ParkingSessionException("Ban khong co quyen truy cap chuc nang nay");
}

public void assertSameBranch(Long targetBranchId) {
    User user = currentUserService.getCurrentUser();

    if (user.getUserRole() == UserRole.ADMIN) {
        return;
    }

    if (user.getUserRole() == UserRole.MANAGER || user.getUserRole() == UserRole.STAFF) {
        Long userBranchId = requireUserBranchId(user);
        if (!userBranchId.equals(targetBranchId)) {
            throw new ParkingSessionException("Ban khong co quyen thao tac tren chi nhanh nay");
        }
        return;
    }

    throw new ParkingSessionException("Ban khong co quyen thao tac chuc nang nay");
}

private Long requireUserBranchId(User user) {
    if (user.getParkingBranch() == null) {
        throw new ParkingSessionException("Tai khoan chua duoc gan chi nhanh");
    }
    return user.getParkingBranch().getParkingBranchId();
}
```

Nen tach 2 ham vi:

- `resolveReadableBranchId(...)`: dung cho list/filter, co the nhan `branchId = null`.
- `assertSameBranch(...)`: dung cho get/update/delete/action tren mot entity da co branch.

## 5. Ranh gioi quyen de xuat

### ADMIN

- Xem tat ca branch, session, booking, incident, payment.
- Tao/sua/xoa branch, floor, zone, card, price policy.
- Tao manager/staff va gan branch.

### MANAGER

- Chi xem branch duoc gan.
- Chi xem session, booking, card, floor, zone, incident cua branch duoc gan.
- Co the quan ly staff/incident trong branch neu nghiep vu yeu cau.
- Khong duoc truy cap hoac thay doi du lieu branch khac.

### STAFF

- Chi thao tac check-in/check-out, booking check-in, incident trong branch duoc gan.
- Khong duoc xem dashboard/tong hop cua branch khac.
- Khong duoc thay doi cau hinh branch/floor/zone/card neu khong duoc cap quyen rieng.

### USER

- Chi xem du lieu cua chinh minh.
- Tao booking theo branch user chon.
- Xem `my-bookings`.
- Tao incident/lost card cua chinh minh.

## 6. Checklist implement theo thu tu uu tien

### 6.1. Khoa security endpoint truoc

Trong `SecurityConfig`, chi nen public:

- `/api/auth/**`
- Swagger neu can test
- API public that su, vi du `GET /api/parking-branches` hoac `GET /api/vehicle-types`
- VNPay callback/return/ipn neu can public

Khong nen `permitAll` cac API quan ly:

- `/api/parking-sessions/**`
- `/api/parking-branches/**`
- `/api/parking-floors/**`
- `/api/parking-zones/**`
- `/api/parking-cards/**`
- `/api/bookings/**`
- `/api/incident-reports/**`
- `/api/payments/**`, tru callback cong thanh toan

Sau do them `@PreAuthorize` tai controller cho rule ro rang:

```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
@PreAuthorize("hasRole('USER')")
```

Luu y: can kiem tra `User.getAuthorities()` dang tra role theo format nao. Neu authority hien la `ADMIN` thay vi `ROLE_ADMIN`, dung `hasAuthority('ADMIN')` thay cho `hasRole('ADMIN')`.

### 6.2. Branch scope cho API doc du lieu

#### ParkingBranchService

Hien tai:

```java
getAllParkingBranches() -> findAll()
getParkingBranchById(id) -> findBranch(id)
```

Nen doi:

- `ADMIN`: `findAll()`.
- `MANAGER/STAFF`: chi return branch cua current user.
- `getParkingBranchById(id)`: manager/staff chi duoc xem neu `id == currentUser.parkingBranchId`.

#### ParkingSessionService

Hien tai `getAllParkingSession()` can duoc scope theo branch.

Can them repository:

```java
List<ParkingSession> findByParkingBranchParkingBranchId(Long parkingBranchId);
Optional<ParkingSession> findByParkingSessionIdAndParkingBranchParkingBranchId(Long sessionId, Long parkingBranchId);
```

Rule:

- `ADMIN`: xem all hoac filter theo branch.
- `MANAGER/STAFF`: chi xem session branch duoc gan.
- Check-in/check-out phai dam bao session/card/branch cung thuoc branch cua staff.

#### BookingService

Hien tai co cac method dem booking theo branch, nhung thieu list/get theo branch cho man hinh quan ly.

Can them repository:

```java
List<Booking> findByParkingBranchParkingBranchIdOrderByCreatedAtDesc(Long parkingBranchId);
Optional<Booking> findByBookingCodeIgnoreCaseAndParkingBranchParkingBranchId(String bookingCode, Long parkingBranchId);
```

Rule:

- `ADMIN`: xem all booking.
- `MANAGER/STAFF`: xem booking cua branch minh.
- Khi staff quet booking code, booking code thuoc branch khac phai bi tu choi.

#### IncidentReportService

Can implement ngay trong `getAllIncidents()`:

```java
Long scopedBranchId = branchScopeService.resolveReadableBranchId(branchId);

return incidentReportRepository.findByFilters(
        scopedBranchId, status, type, priority, startDate, endDate, assignedStaffId, pageable)
        .map(this::convertToResponse);
```

Voi `getReportById(id)`:

- `USER`: chi xem incident minh tao.
- `MANAGER/STAFF`: chi xem incident thuoc branch minh.
- `ADMIN`: xem tat ca.

Voi `assignIncident`, `resolveIncident`, `cancelIncident`:

- Phai check `report.getParkingBranch().getParkingBranchId()` bang branch cua manager/staff.
- Khi assign staff, staff duoc assign cung phai thuoc cung branch voi incident.

### 6.3. Branch scope cho API ghi du lieu

#### ParkingCard/Floor/Zone

Cac service nay dang nhan `parkingBranchId` tu request/path.

Can bo sung:

- Manager/staff khong duoc create/update card/floor/zone cho branch khac.
- Khi list theo branch, manager/staff khong duoc truyen branch khac.
- Khi update/delete object da ton tai, phai check object do thuoc branch cua current user.

#### Payment/Checkout

`PaymentService` xu ly payment dua tren `ParkingSession`.

Can dam bao truoc khi staff checkout:

- Session thuoc branch cua staff.
- Card thuoc branch cua staff.
- Payment callback VNPay co the public, nhung API truy van payment noi bo phai co auth va branch scope.

## 7. Repository methods nen bo sung

### ParkingSessionRepository

```java
List<ParkingSession> findByParkingBranchParkingBranchId(Long parkingBranchId);
Optional<ParkingSession> findByParkingSessionIdAndParkingBranchParkingBranchId(Long sessionId, Long parkingBranchId);
```

### BookingRepository

```java
List<Booking> findByParkingBranchParkingBranchIdOrderByCreatedAtDesc(Long parkingBranchId);
Optional<Booking> findByBookingCodeIgnoreCaseAndParkingBranchParkingBranchId(String bookingCode, Long parkingBranchId);
```

### ParkingCardRepository

Hien da co:

```java
Optional<ParkingCard> findByCardCodeAndParkingBranchParkingBranchId(String cardCode, Long parkingBranchId);
```

Nen bo sung them:

```java
List<ParkingCard> findByParkingBranchParkingBranchId(Long parkingBranchId);
Optional<ParkingCard> findByParkingCardIdAndParkingBranchParkingBranchId(Long cardId, Long parkingBranchId);
```

### IncidentReportRepository

Hien da co filter theo `branchId`. Khong can them query moi cho list, chi can service truyen `scopedBranchId`.

Neu muon toi uu get/detail co the them:

```java
Optional<IncidentReport> findByIncidentIdAndParkingBranchParkingBranchId(Long incidentId, Long parkingBranchId);
```

## 8. Test case can co

### Security

1. Goi `/api/parking-sessions/**` khong token phai bi 401.
2. Goi API quan ly bang role `USER` phai bi 403 neu khong duoc phep.
3. Goi API manager branch A voi `branchId = B` phai bi tu choi.

### Branch

1. Admin xem duoc tat ca branch.
2. Manager branch A login, response co `parkingBranchId = A`.
3. Manager branch A list branch chi nhan branch A.
4. Manager branch A get branch B bi tu choi.

### Parking session

1. Admin xem duoc session cua branch A va B.
2. Manager branch A chi xem session branch A.
3. Manager branch A khong checkout duoc session branch B.
4. Staff branch A khong check-in bang card thuoc branch B.

### Booking

1. Admin xem tat ca booking.
2. Manager branch A chi xem booking branch A.
3. Staff branch A quet booking code cua branch A thanh cong.
4. Staff branch A quet booking code cua branch B bi tu choi.

### Incident

1. User chi xem incident cua minh.
2. Staff branch A chi xem incident branch A.
3. Manager branch A chi assign/resolve/cancel incident branch A.
4. Manager branch A khong assign staff branch B vao incident branch A.
5. Admin xem va thao tac duoc tat ca incident.

## 9. Thu tu lam khuyen nghi

1. Sua `SecurityConfig`: bo `permitAll` khoi cac API quan ly.
2. Tao `CurrentUserService` va `BranchScopeService`.
3. Sua API doc du lieu: branch, session, booking, incident.
4. Sua API ghi du lieu: card, floor, zone, check-in/check-out, incident actions.
5. Them repository methods con thieu.
6. Them test cho role va branch scope.

## 10. Ghi chu ve booking va capacity

He thong da co logic booking giu cho theo capacity:

```java
activeSessions + activeBookings >= totalCapacity
```

Va `ParkingBranchService` nen tinh:

```java
availableCapacity = totalCapacity - activeSessions - activeBookings
```

Khi manager/staff dang nhap, API dashboard/capacity chi nen tinh tren branch cua tai khoan do. `ADMIN` moi duoc xem tong hop nhieu branch.

## 11. Ket luan

Huong hien tai dung ve data model, nhung chua du ve security va branch isolation.

Cach toi uu la dua tat ca API quan ly ve mot cong thuc chung:

```text
Du lieu duoc doc/ghi = role cua current user + parkingBranch cua current user
```

Khi `SecurityConfig`, `CurrentUserService`, va `BranchScopeService` duoc ap dung dong bo, manager/staff se chi lam viec trong dung chi nhanh duoc gan, ke ca khi frontend gui sai hoac co tinh gui `parkingBranchId` cua chi nhanh khac.
