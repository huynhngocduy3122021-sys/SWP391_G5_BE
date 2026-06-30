# Bao cao phan quyen du lieu theo ParkingBranch cho Manager/Staff

Ngay lap: 2026-06-30

## 1. Muc tieu

Tai lieu nay ghi lai hien trang logic he thong khi dang nhap bang tai khoan `MANAGER` hoac `STAFF` duoc gan voi mot `ParkingBranch` cu the.

Muc tieu mong muon:

- `ADMIN` xem va quan ly du lieu toan he thong.
- `MANAGER` chi xem va quan ly du lieu cua chi nhanh minh phu trach.
- `STAFF` chi thao tac trong chi nhanh minh duoc gan.
- Backend phai tu enforce theo user dang dang nhap, khong chi dua vao frontend gui `parkingBranchId`.

## 2. Hien trang trong code

### 2.1. User co gan ParkingBranch

Model `User` da co lien ket:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parking_branch_id")
private ParkingBranch parkingBranch;
```

Khi admin tao staff/manager, `UserService` da gan chi nhanh:

```java
newStaff.setUserRole(UserRole.STAFF);
newStaff.setParkingBranch(branch);

newManager.setUserRole(UserRole.MANAGER);
newManager.setParkingBranch(branch);
```

Khi login, `UserResponse` co tra:

```java
private Long parkingBranchId;
private String parkingBranchName;
```

Ket luan: du lieu goc de xac dinh manager/staff thuoc chi nhanh nao da co.

### 2.2. Backend chua enforce scope theo ParkingBranch o nhieu API

Nhieu service hien dang lay du lieu toan bo he thong:

```java
parkingBranchRepository.findAll()
parkingSessionRepository.findAll()
bookingRepository.findAll()
```

Dieu nay co nghia la neu frontend goi cac API list, manager cua branch A van co the nhin thay du lieu branch B, neu endpoint do khong co logic loc theo current user's branch.

### 2.3. SecurityConfig dang mo qua rong

Trong `SecurityConfig`, nhieu API quan ly dang de `permitAll`:

```java
"/api/parking-sessions/**",
"/api/parking-branches/**",
"/api/parking-floors/**",
"/api/parking-zones/**",
"/api/parking-cards/**",
"/api/payments/**"
```

He qua:

- Cac API nay khong bat buoc login.
- Backend khong co current user de biet user la `ADMIN`, `MANAGER`, hay `STAFF`.
- Manager branch A/B khong duoc tach scope an toan o backend.
- Nguoi khong dang nhap van co the goi mot so API quan ly neu khong bi chan o controller/service.

### 2.4. IncidentReport co y dinh loc nhung chua implement day du

Trong `IncidentReportService.getAllIncidents()` co comment ve viec staff/manager chi xem chi nhanh cua ho:

```java
if (user.getUserRole() == UserRole.STAFF || user.getUserRole() == UserRole.MANAGER) {
    // Staff/Manager chi duoc loc su co cua chi nhanh cua ho ...
}
```

Nhung hien tai code van goi:

```java
incidentReportRepository.findByFilters(branchId, ...)
```

`branchId` van la gia tri tu request, chua bi override bang `user.getParkingBranch().getParkingBranchId()`.

## 3. Ket luan hien tai

Hien tai he thong **co luu manager/staff thuoc branch nao**, nhung **chua dam bao backend chi tra du lieu branch do**.

Neu frontend sau login tu lay `parkingBranchId` roi truyen vao API filter thi co the hien thi dung o UI, nhung ve bao mat va logic backend thi chua du.

Backend can tu enforce:

- Manager/staff khong duoc xem branch khac.
- Manager/staff khong duoc sua/xoa/tac dong len branch khac.
- Neu frontend co tinh gui `branchId` khac, backend van phai bo qua hoac tu choi.

## 4. Ranh gioi quyen de xuat

### ADMIN

- Xem tat ca parking branch.
- Xem tat ca parking session.
- Xem tat ca booking.
- Xem tat ca incident.
- Tao/sua/xoa branch, floor, zone, card, price policy.
- Tao manager/staff va gan branch.

### MANAGER

- Chi xem branch duoc gan.
- Chi xem session, booking, card, floor, zone, incident cua branch duoc gan.
- Co the quan ly staff va incident trong branch neu nghiep vu yeu cau.
- Khong duoc truy cap du lieu branch khac.

### STAFF

- Chi thao tac check-in/check-out, booking check-in, incident trong branch duoc gan.
- Khong duoc xem dashboard/tong hop cua branch khac.
- Khong duoc thay doi cau hinh branch neu khong duoc cap quyen rieng.

### USER

- Chi xem du lieu cua chinh minh.
- Tao booking theo branch user chon.
- Xem `my-bookings`.
- Tao incident/lost card cua chinh minh.

## 5. Huong thiet ke nen ap dung

### 5.1. Tao helper lay current user

Nen tao mot service/helper dung chung, vi hien tai moi service tu viet rieng logic lay user.

Vi du:

```java
public User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()
            || "anonymousUser".equals(authentication.getPrincipal())) {
        throw new ParkingSessionException("Yeu cau dang nhap");
    }
    return (User) authentication.getPrincipal();
}
```

Co the dat trong:

- `CurrentUserService`
- `SecurityUtils`
- hoac base helper rieng cho service layer

### 5.2. Tao ham resolve branch scope

Nen co mot ham chuan de quyet dinh branch nao duoc phep truy cap:

```java
private Long resolveBranchScope(Long requestedBranchId) {
    User user = currentUserService.getCurrentUser();

    if (user.getUserRole() == UserRole.ADMIN) {
        return requestedBranchId;
    }

    if (user.getUserRole() == UserRole.MANAGER || user.getUserRole() == UserRole.STAFF) {
        if (user.getParkingBranch() == null) {
            throw new ParkingSessionException("Tai khoan chua duoc gan chi nhanh");
        }

        Long userBranchId = user.getParkingBranch().getParkingBranchId();

        if (requestedBranchId != null && !requestedBranchId.equals(userBranchId)) {
            throw new ParkingSessionException("Ban khong co quyen truy cap chi nhanh nay");
        }

        return userBranchId;
    }

    throw new ParkingSessionException("Ban khong co quyen truy cap chuc nang nay");
}
```

Y nghia:

- `ADMIN`: duoc dung `branchId` tu request, hoac null de lay tat ca.
- `MANAGER/STAFF`: luon bi ep ve branch cua tai khoan.
- Neu manager/staff gui branch khac: bao loi.

## 6. Checklist can sua

### 6.1. SecurityConfig

Can go bo `permitAll` cho cac API quan ly.

Chi nen public:

- `/api/auth/login`
- `/api/auth/register` neu cho user tu dang ky
- Swagger neu can test
- API public thuc su, neu co
- VNPay return/ipn neu can callback public

Khong nen public:

- `/api/parking-sessions/**`
- `/api/parking-branches/**`
- `/api/parking-floors/**`
- `/api/parking-zones/**`
- `/api/parking-cards/**`
- `/api/bookings/**` ngoai tru cac endpoint user co token
- `/api/incident-reports/**`

### 6.2. ParkingBranchService

Hien tai:

```java
getAllParkingBranches() -> findAll()
getParkingBranchById(id) -> findBranch(id)
```

Nen doi:

- `ADMIN`: `findAll()`.
- `MANAGER/STAFF`: chi return branch cua current user.
- `getParkingBranchById(id)`: manager/staff chi duoc xem neu `id == currentUser.parkingBranchId`.

### 6.3. ParkingSessionService

Hien tai:

```java
getAllParkingSession() -> parkingSessionRepository.findAll()
```

Can them repository:

```java
List<ParkingSession> findByParkingBranchParkingBranchId(Long branchId);
```

Sau do:

- `ADMIN`: lay all hoac theo filter.
- `MANAGER/STAFF`: chi lay session cua branch duoc gan.

Voi `guestCheckIn`, can dam bao card thuoc branch cua staff dang thao tac. Neu endpoint nay cho staff dung, backend nen check:

```java
parkingCard.getParkingBranch().getParkingBranchId()
    .equals(currentUser.getParkingBranch().getParkingBranchId())
```

### 6.4. BookingService

Hien tai:

```java
getAllBookings() -> bookingRepository.findAll()
getBookingByCode(code) -> findByBookingCodeIgnoreCase(code)
```

Can them repository:

```java
List<Booking> findByParkingBranchParkingBranchIdOrderByCreatedAtDesc(Long branchId);
```

De xuat:

- `ADMIN`: xem all booking.
- `MANAGER/STAFF`: xem booking cua branch minh.
- `getBookingByCode`: neu manager/staff quet QR booking cua branch khac thi tu choi.

### 6.5. IncidentReportService

Can implement comment dang con dang do:

```java
if (user.getUserRole() == UserRole.STAFF || user.getUserRole() == UserRole.MANAGER) {
    branchId = user.getParkingBranch().getParkingBranchId();
}
```

Va voi `getReportById(id)`:

- `USER`: chi xem incident minh tao.
- `STAFF/MANAGER`: chi xem incident thuoc branch minh.
- `ADMIN`: xem tat ca.

### 6.6. ParkingCard/Floor/Zone

Cac service nay dang nhan `parkingBranchId` tu request/path.

Can bo sung:

- Manager/staff khong duoc create/update card/floor/zone cho branch khac.
- Khi list theo branch, manager/staff khong duoc truyen branch khac.
- Khi update object da ton tai, phai check object do thuoc branch cua current user.

### 6.7. Payment/Checkout

`PaymentService` xu ly payment dua tren `ParkingSession`.

Can dam bao truoc khi staff checkout:

- Session thuoc branch cua staff.
- Card thuoc branch cua staff.
- Payment callback VNPay co the public, nhung khong nen expose API truy van payment noi bo neu chua auth.

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

```java
List<ParkingCard> findByParkingBranchParkingBranchId(Long parkingBranchId);
Optional<ParkingCard> findByParkingCardIdAndParkingBranchParkingBranchId(Long cardId, Long parkingBranchId);
```

### IncidentReportRepository

Da co filter theo `branchId`, chi can service override branch id dung role.

## 8. Test case can co

### Dang nhap va scope

1. Admin login nhan token, xem duoc tat ca branch.
2. Manager branch A login, response co `parkingBranchId = A`.
3. Manager branch A goi API list branch, chi nhan branch A.
4. Manager branch A goi API branch B, bi tu choi.

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
4. Admin xem va thao tac duoc tat ca incident.

### Security

1. Goi API `/api/parking-sessions` khong token phai bi 401.
2. Goi API quan ly bang role USER phai bi 403 neu khong duoc phep.
3. Goi API manager branch A voi `branchId = B` phai bi 403 hoac business exception.

## 9. Uu tien phat trien

### Uu tien 1: Khoa security endpoint

Go bo `permitAll` cho cac API quan ly va them `@PreAuthorize` vao controller.

### Uu tien 2: Branch scope cho list/get

Sua cac API doc du lieu:

- `ParkingBranchService`
- `ParkingSessionService`
- `BookingService`
- `IncidentReportService`

### Uu tien 3: Branch scope cho write action

Sua cac API tao/sua/xoa:

- parking card
- parking floor
- parking zone
- incident assign/resolve/cancel
- check-in/check-out

### Uu tien 4: Test

Them integration test hoac service test cho role `ADMIN`, `MANAGER`, `STAFF`, `USER`.

## 10. Ghi chu ve booking va capacity

He thong da co logic booking giu cho theo capacity:

```java
activeSessions + activeBookings >= totalCapacity
```

Va `ParkingBranchService` da nen tinh `availableCapacity` theo:

```java
availableCapacity = totalCapacity - activeSessions - activeBookings
```

Tuy nhien can dam bao API hien thi trang chu/dashboard khi manager dang nhap chi tinh capacity cua branch manager do, khong tinh tong tat ca branch.

## 11. Ket luan

Hien tai he thong moi dung o muc "user co thong tin branch", chua dat muc "backend enforce branch isolation".

Huong phat trien tiep theo la dua toan bo API quan ly ve nguyen tac:

```text
Du lieu tra ve = quyen role + parkingBranch cua current user
```

Khi lam duoc viec nay, manager cua moi `ParkingBranch` se dang nhap vao dung pham vi chi nhanh cua minh, thay vi vo tinh xem du lieu tong cua tat ca chi nhanh.
