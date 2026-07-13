package Parking.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import Parking.Model.Booking;
import Parking.Model.ParkingBranch;
import Parking.Model.ParkingSession;
import Parking.Model.User;
import Parking.Model.Vehicle;
import Parking.Model.VehicleType;
import Parking.enums.BookingStatus;
import Parking.enums.ParkingSessionStatus;
import Parking.Repository.BookingRepository;
import Parking.Repository.UserRepository;
import Parking.Repository.ParkingBranchRepository;
import Parking.Repository.ParkingSessionRepository;
import Parking.Repository.VehicleRepository;
import Parking.Repository.VehicleTypeRepository;
import Parking.dto.request.CreateBookingRequest;
import Parking.dto.response.BookingResponse;
import Parking.exception.exceptions.BookingException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ParkingBranchRepository parkingBranchRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final VehicleRepository vehicleRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final BranchScopeService branchScopeService;

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        // 1. Lấy thông tin user hiện tại từ Security Context
        User user = getCurrentUser();

        // 2. Chống Race Condition bằng cách Lock chi nhánh khi đếm chỗ trống và tạo booking
        ParkingBranch branch = parkingBranchRepository.findAndLockByParkingBranchId(request.getParkingBranchId())
                .orElseThrow(() -> new BookingException("Chi nhánh bãi xe không tồn tại"));

        if (!branch.isActive()) {
            throw new BookingException("Chi nhánh bãi xe hiện đang tạm đóng");
        }

        // 3. Kiểm tra loại xe
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new BookingException("Loại phương tiện không tồn tại"));

        // Rule 2.1: Chỉ cho phép booking xe CAR hoặc ELECTRIC_CAR
        String typeName = vehicleType.getTypeName();
        if (!"CAR".equalsIgnoreCase(typeName) && !"ELECTRIC_CAR".equalsIgnoreCase(typeName)) {
            throw new BookingException("Chức năng đặt chỗ trước chỉ áp dụng cho Ô tô và Ô tô điện. Xe máy/Xe máy điện không được phép đặt trước.");
        }

        // 4. Ràng buộc về thời gian đặt chỗ (Rule 2.3)
        LocalDateTime now = LocalDateTime.now();
        if (request.getExpectedArrivalTime().isBefore(now.plusMinutes(15))) {
            throw new BookingException("Thời gian dự kiến đến phải sau thời gian hiện tại ít nhất 15 phút.");
        }
        if (request.getExpectedArrivalTime().isAfter(now.plusMinutes(60))) {
            throw new BookingException("Thời gian dự kiến đến không được quá 1 tiếng kể từ hiện tại.");
        }

        // 5. Kiểm tra biển số xe và đăng ký/gán xe (Rule 2.2)
        String licensePlate = request.getLicensePlate().trim().toUpperCase();
        Vehicle vehicle = vehicleRepository.findByLicensePlateIgnoreCase(licensePlate)
                .orElseGet(() -> {
                    Vehicle newVehicle = new Vehicle();
                    newVehicle.setLicensePlate(licensePlate);
                    newVehicle.setVehicleType(vehicleType);
                    newVehicle.setUser(user);
                    newVehicle.setVehicleColor(request.getVehicleColor() != null ? request.getVehicleColor().trim() : null);
                    newVehicle.setVehicleBrand(request.getVehicleBrand() != null ? request.getVehicleBrand().trim() : null);
                    newVehicle.setVehicleSource(Parking.enums.VehicleSource.GUEST);
                    newVehicle.setDeleted(false);
                    return vehicleRepository.save(newVehicle);
                });

        if (vehicle.getUser() != null && !vehicle.getUser().getUserId().equals(user.getUserId())) {
            throw new BookingException("Phương tiện này đã được đăng ký bởi người dùng khác.");
        }

        // Cập nhật thông tin màu xe / hiệu xe nếu chưa có
        boolean needsUpdate = false;
        if ((vehicle.getVehicleColor() == null || vehicle.getVehicleColor().isBlank()) && request.getVehicleColor() != null && !request.getVehicleColor().isBlank()) {
            vehicle.setVehicleColor(request.getVehicleColor().trim());
            needsUpdate = true;
        }
        if ((vehicle.getVehicleBrand() == null || vehicle.getVehicleBrand().isBlank()) && request.getVehicleBrand() != null && !request.getVehicleBrand().isBlank()) {
            vehicle.setVehicleBrand(request.getVehicleBrand().trim());
            needsUpdate = true;
        }
        if (needsUpdate) {
            vehicleRepository.save(vehicle);
        }

        // 6. Kiểm tra giới hạn số lượng booking active của user (tối đa 3 booking hoạt động)
        long activeBookingsCount = bookingRepository.countByUserUserIdAndStatusIn(user.getUserId(), 
                List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING));
        if (activeBookingsCount >= 3) {
            throw new BookingException("Mỗi tài khoản người dùng chỉ được có tối đa 3 đặt chỗ đang hoạt động. Vui lòng hạn chế đặt chỗ quá nhiều.");
        }

        boolean vehicleHasActive = bookingRepository.existsByVehicleVehiclesIdAndStatusIn(vehicle.getVehiclesId(), 
                List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING));
        if (vehicleHasActive) {
            throw new BookingException("Phương tiện này đã có một đặt chỗ đang hoạt động.");
        }

        // 7. Kiểm tra dung lượng bãi đỗ thực tế bao gồm cả các booking CONFIRMED khác trong hold window (Rule 3)
        Long totalCapacityLong = parkingZoneRepository.calculateTotalCapacity(branch.getParkingBranchId(), vehicleType.getVehicleTypeId());
        int totalCapacity = totalCapacityLong != null ? totalCapacityLong.intValue() : 0;
        if (totalCapacity <= 0) {
            throw new BookingException("Chi nhánh bãi xe này không hỗ trợ đỗ xe hoặc không có khu vực hoạt động cho loại xe " + vehicleType.getDescription());
        }

        long activeSessions = parkingSessionRepository.countByParkingBranchParkingBranchIdAndVehicleVehicleTypeVehicleTypeIdAndStatus(
                branch.getParkingBranchId(), vehicleType.getVehicleTypeId(), ParkingSessionStatus.ACTIVE);

        long activeBookings = bookingRepository.countActiveBookings(
                branch.getParkingBranchId(), vehicleType.getVehicleTypeId(), now);

        if (activeSessions + activeBookings >= totalCapacity) {
            throw new BookingException("Hết chỗ đỗ khả dụng cho loại xe này tại chi nhánh. Vui lòng chọn thời gian khác hoặc chi nhánh khác.");
        }

        // Tạo Booking
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setParkingBranch(branch);
        booking.setVehicle(vehicle);
        booking.setVehicleType(vehicleType);
        booking.setExpectedArrivalTime(request.getExpectedArrivalTime());
        booking.setHoldUntil(request.getExpectedArrivalTime().plusMinutes(60)); // Grace period 60m
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);

        // Sinh mã booking Code duy nhất
        String bookingCode;
        do {
            bookingCode = "BK" + String.format("%08d", new java.util.Random().nextInt(100000000));
        } while (bookingRepository.findByBookingCodeIgnoreCase(bookingCode).isPresent());
        booking.setBookingCode(bookingCode);

        booking = bookingRepository.save(booking);
        return convertToResponse(booking);
    }

    // Cần inject thêm ParkingZoneRepository để thực hiện calculateTotalCapacity
    private final Parking.Repository.ParkingZoneRepository parkingZoneRepository;

    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings() {
        User user = getCurrentUser();
        return bookingRepository.findByUserUserIdOrderByCreatedAtDesc(user.getUserId())
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings() {
        Long branchId = branchScopeService.resolveReadableBranchId(null);
        return bookingRepository.findAllByBranchId(branchId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId) {
        User user = getCurrentUser();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingException("Đặt chỗ không tồn tại"));

        // Kiểm tra quyền hủy (Chỉ chủ sở hữu hoặc ADMIN/STAFF/MANAGER mới được hủy)
        boolean isOwner = booking.getUser().getUserId().equals(user.getUserId());
        boolean isPrivileged = "ADMIN".equalsIgnoreCase(user.getUserRole().name()) 
                || "MANAGER".equalsIgnoreCase(user.getUserRole().name())
                || "STAFF".equalsIgnoreCase(user.getUserRole().name());

        if (!isOwner) {
            if (!isPrivileged) {
                throw new BookingException("Bạn không có quyền hủy đặt chỗ này.");
            }
            branchScopeService.assertSameBranch(booking.getParkingBranch().getParkingBranchId());
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingException("Không thể hủy đặt chỗ có trạng thái: " + booking.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setUpdatedAt(now);

        return convertToResponse(bookingRepository.save(booking));
    }

    // Scheduler tự động quét dọn dẹp các booking quá hạn giữ chỗ (Rule 5.2)
    @Scheduled(cron = "0 */5 * * * *") // Chạy 5 phút 1 lần
    @Transactional
    public void cleanupExpiredBookings() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> expiredBookings = bookingRepository.findByStatusAndHoldUntilBefore(BookingStatus.CONFIRMED, now);
        for (Booking booking : expiredBookings) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setExpiredAt(now);
            booking.setUpdatedAt(now);
            bookingRepository.save(booking);

            // Tăng số lần vi phạm của user đặt chỗ quá hạn
            User user = booking.getUser();
            if (user != null) {
                user.setViolationCount(user.getViolationCount() + 1);
                if (user.getViolationCount() >= 3) {
                    user.setLocked(true);
                }
                userRepository.save(user);
            }
        }
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingByCode(String code) {
        Booking booking = bookingRepository.findByBookingCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BookingException("Mã đặt chỗ không tồn tại"));

        User user = getCurrentUser();
        boolean isOwner = booking.getUser().getUserId().equals(user.getUserId());
        if (!isOwner) {
            branchScopeService.assertSameBranch(booking.getParkingBranch().getParkingBranchId());
        }

        return convertToResponse(booking);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BookingException("Người dùng chưa được xác thực");
        }
        return (User) authentication.getPrincipal();
    }

    private BookingResponse convertToResponse(Booking booking) {
        return BookingResponse.builder()
                .bookingId(booking.getBookingId())
                .bookingCode(booking.getBookingCode())
                .userId(booking.getUser().getUserId())
                .userFullName(booking.getUser().getUserFullName())
                .parkingBranchId(booking.getParkingBranch().getParkingBranchId())
                .parkingBranchName(booking.getParkingBranch().getBranchName())
                .vehicleId(booking.getVehicle().getVehiclesId())
                .licensePlate(booking.getVehicle().getLicensePlate())
                .vehicleColor(booking.getVehicle().getVehicleColor())
                .vehicleBrand(booking.getVehicle().getVehicleBrand())
                .vehicleTypeId(booking.getVehicleType().getVehicleTypeId())
                .vehicleTypeName(booking.getVehicleType().getTypeName())
                .parkingSessionId(booking.getParkingSession() != null ? booking.getParkingSession().getParkingSessionId() : null)
                .expectedArrivalTime(booking.getExpectedArrivalTime())
                .holdUntil(booking.getHoldUntil())
                .status(booking.getStatus().name())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .cancelledAt(booking.getCancelledAt())
                .completedAt(booking.getCompletedAt())
                .expiredAt(booking.getExpiredAt())
                .build();
    }
}
