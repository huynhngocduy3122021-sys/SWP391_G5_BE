package Parking.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import javax.xml.datatype.DatatypeConfigurationException;

import org.hibernate.validator.constraints.time.DurationMax;
import org.springframework.boot.autoconfigure.jms.JmsProperties.Listener.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import Parking.Repository.ParkingBranchRepository;
import Parking.Repository.ParkingCardRepository;
import Parking.Repository.ParkingSessionRepository;
import Parking.Repository.ParkingZoneRepository;
import Parking.Repository.PaymentRepository;
import Parking.Repository.PricePolicyRepository;
import Parking.Repository.VehicleRepository;
import Parking.Repository.VehicleTypeRepository;
import Parking.Repository.BookingRepository;
import Parking.Model.Booking;
import Parking.dto.request.GuestCheckInRequest;
import Parking.dto.request.GuestCheckOutRequest;
import Parking.dto.response.GuestCheckOutResponse;
import Parking.dto.response.ParkingSessionResponse;
import Parking.dto.response.UserResponse;
import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingSessionStatus;
import Parking.enums.BookingStatus;
import Parking.enums.PaymentStatus;
import Parking.enums.VehicleSource;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import Parking.exception.exceptions.ParkingSessionException;
import Parking.Model.ParkingBranch;
import Parking.Model.ParkingCard;
import Parking.Model.ParkingSession;
import Parking.Model.ParkingZone;
import Parking.Model.Payment;
import Parking.Model.PricePolicy;
import Parking.Model.Vehicle;
import Parking.Model.VehicleType;
import Parking.Model.VehicleImage;
import Parking.Model.VehicleImageType;
import java.util.List;
import java.util.ArrayList;


@Service
@RequiredArgsConstructor
@Transactional
public class ParkingSessionService {
      private final ParkingBranchRepository parkingBranchRepository;

    private final ParkingCardRepository parkingCardRepository;

    private final ParkingSessionRepository parkingSessionRepository;

    private final ParkingZoneRepository parkingZoneRepository;

    private final VehicleRepository vehicleRepository;

    private final VehicleTypeRepository vehicleTypeRepository;

    private final BookingRepository bookingRepository;

    private final PricePolicyRepository pricePolicyRepository;

    private final PaymentRepository paymentRepository;

    private final PaymentService paymentService;

    public ParkingSessionResponse guestCheckIn(GuestCheckInRequest request) { // hàm tạo guest check in
        
        
        String licesePlate = normalizeLicensePlate(request.getLicensePlate());
        String cardCode = normalizeCardCode(request.getCardCode());

        // b1 : tim va khoa the

        ParkingCard parkingCard = parkingCardRepository.findByCardCodeIgnoreCase(cardCode)
                                .orElseThrow(() -> new ParkingSessionException("Parking card not found"));

        if(parkingCard.getStatus() != ParkingCardStatus.AVAILABLE) {
            throw new ParkingSessionException("Parking card is not available");
        }

        // b2 : lay va khoa chi nhanh

        if(parkingCard.getParkingBranch() == null) {
            throw new ParkingSessionException("Parking card does not belong to any branch");
        }

        Long barnchId = parkingCard.getParkingBranch().getParkingBranchId();

        ParkingBranch parkingBranch = parkingBranchRepository.findByParkingBranchId(barnchId)
                                    .orElseThrow(() -> new ParkingSessionException("Parking branch not found"));
        
        if(!parkingBranch.isActive()) {
            throw new ParkingSessionException("Parking branch is inactive");
        }

        // b3 : tim loai xe

        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                            .orElseThrow(() -> new ParkingSessionException("Vehicle type not found"));

        //b4 : tim hoac tao phuong tien

            Vehicle vehicle = vehicleRepository.findByLicensePlateIgnoreCase(licesePlate)
                        .orElseGet(() -> createGuestVehicle(request, licesePlate, vehicleType));

            // kiem tra xe : mac du da ton tai nhung loai xe FE gui ko giong loai xe
            
            if(!vehicle.getVehicleType().getVehicleTypeId().equals(vehicleType.getVehicleTypeId())) {
                throw new ParkingSessionException("Vehicle type does not match existing vehicle information");
            }
            // b5 kiem tra xe trong bai chua
              boolean vehicleHasActiveSession = parkingSessionRepository.existsByVehicleVehiclesIdAndStatus(vehicle.getVehiclesId(),ParkingSessionStatus.ACTIVE);

            if (vehicleHasActiveSession) {
                throw new ParkingSessionException( "Vehicle already has an active parking session");
                }

            //b6 : kiem tra the co session hay chua
            boolean cardHasActiveSession =parkingSessionRepository.existsByParkingCardParkingCardIdAndStatus(parkingCard.getParkingCardId(),ParkingSessionStatus.ACTIVE);

            if (cardHasActiveSession) {
                throw new ParkingSessionException("Parking card is already being used");
            }
            // b7 : tinh tong suc chua cua chi nhanh

             Long totalCapacity = parkingZoneRepository.calculateTotalCapacity(parkingBranch.getParkingBranchId(),vehicleType.getVehicleTypeId());

            if (totalCapacity == null || totalCapacity <= 0) {
                throw new ParkingSessionException("Branch has no active parking zone for this vehicle type");
            }
            //b8 : diem so xe dang active
            long currentVehicleCount = parkingSessionRepository.countByParkingBranchParkingBranchIdAndVehicleVehicleTypeVehicleTypeIdAndStatus(parkingBranch.getParkingBranchId(),vehicleType.getVehicleTypeId(),ParkingSessionStatus.ACTIVE);

            if (currentVehicleCount >= totalCapacity) {
                throw new ParkingSessionException("Parking lot is full for this vehicle type");
            }
            //b9 : tao parking session
            ParkingSession parkingSession =new ParkingSession();

            parkingSession.setParkingBranch(parkingBranch);
            parkingSession.setVehicle(vehicle);
            parkingSession.setParkingCard(parkingCard);
            parkingSession.setCheckInTime(LocalDateTime.now());
            parkingSession.setCheckOutTime(null);
            parkingSession.setTotalAmount(null);
            parkingSession.setStatus(ParkingSessionStatus.ACTIVE);

            parkingSession =parkingSessionRepository.save(parkingSession);

            // b9.5 : check and link active booking
            List<Booking> bookings = bookingRepository.findByVehicleLicensePlateIgnoreCaseAndStatus(licesePlate, BookingStatus.CONFIRMED);
            LocalDateTime now = LocalDateTime.now();
            for (Booking b : bookings) {
                if (!now.isBefore(b.getExpectedArrivalTime().minusMinutes(15)) && !now.isAfter(b.getHoldUntil())) {
                    b.setStatus(BookingStatus.COMPLETED);
                    b.setParkingSession(parkingSession);
                    b.setCompletedAt(now);
                    b.setUpdatedAt(now);
                    bookingRepository.save(b);
                    break;
                }
            }

            //b10 : trang thay the
            parkingCard.setStatus(ParkingCardStatus.IN_USE);

            parkingCardRepository.save(parkingCard);

            return convertToResponse(parkingSession);
        }

        public GuestCheckOutResponse guestCheckOut(GuestCheckOutRequest request, String clientIp) { // hàm check out
            String cardCode = normalizeCardCode(request.getCardCode());
            String exitLicensePlate = normalizeLicensePlate(request.getLicensePlate());

            // b1 : tim va khoa session active
            ParkingSession parkingSession =
                    parkingSessionRepository.findFirstByParkingCardCardCodeIgnoreCaseAndStatus(cardCode,ParkingSessionStatus.ACTIVE)
                        .orElseThrow(() -> new ParkingSessionException("Active parking session not found"));
            //b2 doi chieu ban so
            String storedLicensePlate = normalizeLicensePlate(parkingSession.getVehicle().getLicensePlate());

            if (!storedLicensePlate.equals(exitLicensePlate)) {
                throw new ParkingSessionException("Exit license plate does not match entry license plate");
            }

            // b3: chuyen giao cho PaymentService de tinh tien va thanh toan
            System.out.println(parkingSession + " " + request.getPaymentMethod() + " " + clientIp);
            return paymentService.processCheckOutPayment(parkingSession, request.getPaymentMethod(), clientIp);
        }

        public ParkingSessionResponse getActiveSessionByCard(String cardCode) {
            String normalizedCardCode = normalizeCardCode(cardCode);

            // Tìm session ACTIVE tương ứng với cardCode
            ParkingSession parkingSession =
                    parkingSessionRepository.findFirstByParkingCardCardCodeIgnoreCaseAndStatus(normalizedCardCode, ParkingSessionStatus.ACTIVE)
                            .orElseThrow(() -> new ParkingSessionException("Active parking session not found"));

            ParkingSessionResponse response = convertToResponse(parkingSession);

            // Tính toán tạm tính phí gửi xe dựa trên chính sách giá hiện hành đến thời điểm hiện tại
            try {
                Long vehicleTypeId = parkingSession.getVehicle().getVehicleType().getVehicleTypeId();
                PricePolicy pricePolicy = pricePolicyRepository
                        .findFirstByVehicleTypeVehicleTypeIdAndActiveTrueOrderByPricePolicyIdDesc(vehicleTypeId)
                        .orElse(null);

                if (pricePolicy != null) {
                    BigDecimal tempFee = paymentService.caculateParkingFee(
                            parkingSession.getCheckInTime(),
                            LocalDateTime.now(),
                            pricePolicy
                    );
                    response.setTotalAmount(tempFee);
                }
            } catch (Exception e) {
                // Nếu tính toán lỗi (chưa cấu hình giá,...), giữ nguyên mức phí mặc định
            }

            return response;
        }

        public ParkingSessionResponse getActiveSessionByLicensePlate(String licensePlate) {
            String normalizedPlate = normalizeLicensePlate(licensePlate);
            ParkingSession parkingSession = parkingSessionRepository
                    .findFirstByVehicleLicensePlateIgnoreCaseAndStatus(normalizedPlate, ParkingSessionStatus.ACTIVE)
                    .orElseThrow(() -> new ParkingSessionException("Active parking session not found for this license plate"));
            return convertToResponse(parkingSession);
        }

    public List<ParkingSessionResponse> getAllParkingSession() {
        List<ParkingSession> parkingSessions = parkingSessionRepository.findAll();
        return parkingSessions.stream()
                    .map(this::convertToResponse) // chuyển đổi từng ParkingSession thành ParkingSessionReponse
                    .collect(Collectors.toList()); // thu thập kết quả vào một List<ParkingSessionReponse> và trả về
    }
    
    
    private ParkingSessionResponse convertToResponse(ParkingSession parkingSession) {
        Vehicle vehicle = parkingSession.getVehicle();

        VehicleType vehicleType = vehicle.getVehicleType();

        ParkingCard parkingCard = parkingSession.getParkingCard();

        ParkingBranch parkingBranch = parkingSession.getParkingBranch();

        Payment payment = parkingSession.getPayment();

        List<Long> vehicleImageIds = new ArrayList<>();
        List<String> imageUrls = new ArrayList<>();
        if (parkingSession.getVehicleImages() != null) {
            List<VehicleImage> checkInImages = parkingSession.getVehicleImages().stream()
                    .filter(img -> img.getImageType() == VehicleImageType.CHECK_IN)
                    .toList();

            if (checkInImages.isEmpty()) {
                checkInImages = parkingSession.getVehicleImages();
            }

            for (VehicleImage img : checkInImages) {
                vehicleImageIds.add(img.getVehicleImageId());
                imageUrls.add(img.getImageUrl());
            }
        }

        return ParkingSessionResponse.builder()
            .parkingSessionId(parkingSession.getParkingSessionId())
            .parkingBranchId(parkingBranch.getParkingBranchId())
            .parkingBranchName(parkingBranch.getBranchName())
            .vehicleId(vehicle.getVehiclesId())
            .licensePlate(vehicle.getLicensePlate())
            .vehicleColor(vehicle.getVehicleColor() != null ? vehicle.getVehicleColor() : "Không rõ")
            .vehicleBrand(vehicle.getVehicleBrand() != null ? vehicle.getVehicleBrand() : "Không rõ")
            .vehicleTypeId(vehicleType.getVehicleTypeId())
            .vehicleTypeName( vehicleType.getTypeName())
            .parkingCardId(parkingCard.getParkingCardId())
            .cardCode(parkingCard.getCardCode())
            .checkInTime(parkingSession.getCheckInTime())
            .checkOutTime(parkingSession.getCheckOutTime())
            .totalAmount( parkingSession.getTotalAmount())
            .sessionStatus( parkingSession.getStatus())
            .paymentMethod(payment == null? null: payment.getPaymentMethod())
            .paymentStatus(payment == null ? null: payment.getPaymentStatus())
            .vehicleImageIds(vehicleImageIds)
            .imageUrls(imageUrls)
            .build();
    }

    private String normalizeLicensePlate(String licensePlate) {
        return licensePlate
            .trim()
            .replaceAll("\\s+", "")
            .toUpperCase();
    }

    private String normalizeCardCode(String cardCode) {
        return cardCode
            .trim()
            .toUpperCase();
    }

    private String normalizeOptionalText( String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
     

    private Vehicle createGuestVehicle(GuestCheckInRequest request,String licensePlate,VehicleType vehicleType) {
        Vehicle vehicle = new Vehicle();

        vehicle.setLicensePlate(licensePlate);

        vehicle.setVehicleColor(normalizeOptionalText(request.getVehicleColor()));

        vehicle.setVehicleBrand(normalizeOptionalText(request.getVehicleBrand()));

        vehicle.setVehicleSource(VehicleSource.GUEST);

        vehicle.setUser(null);

        vehicle.setVehicleType(vehicleType);

        return vehicleRepository.save(vehicle);
    }

    public ParkingSessionResponse bookingCheckIn(String bookingCode, String cardCode) {
        cardCode = normalizeCardCode(cardCode);

        // 1. Tìm và khóa thẻ
        ParkingCard parkingCard = parkingCardRepository.findByCardCodeIgnoreCase(cardCode)
                .orElseThrow(() -> new ParkingSessionException("Thẻ gửi xe không tồn tại"));
        if (parkingCard.getStatus() != ParkingCardStatus.AVAILABLE) {
            throw new ParkingSessionException("Thẻ gửi xe hiện không khả dụng");
        }

        // 2. Tìm booking
        Booking booking = bookingRepository.findByBookingCodeIgnoreCase(bookingCode.trim())
                .orElseThrow(() -> new ParkingSessionException("Mã đặt chỗ không tồn tại"));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ParkingSessionException("Đặt chỗ hiện có trạng thái: " + booking.getStatus() + ", không thể check-in");
        }

        // Kiểm tra thời gian hẹn
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(booking.getExpectedArrivalTime().minusMinutes(60))) {
            throw new ParkingSessionException("Quá sớm! Thời gian check-in hợp lệ bắt đầu từ: " + booking.getExpectedArrivalTime().minusMinutes(15));
        }
        if (now.isAfter(booking.getHoldUntil())) {
            throw new ParkingSessionException("Đặt chỗ đã hết hạn giữ chỗ lúc: " + booking.getHoldUntil());
        }

        ParkingBranch parkingBranch = booking.getParkingBranch();
        if (!parkingBranch.isActive()) {
            throw new ParkingSessionException("Chi nhánh bãi xe hiện đang tạm đóng");
        }

        // Kiểm tra xem thẻ có khớp chi nhánh không
        if (parkingCard.getParkingBranch() == null || !parkingCard.getParkingBranch().getParkingBranchId().equals(parkingBranch.getParkingBranchId())) {
            throw new ParkingSessionException("Thẻ gửi xe không thuộc chi nhánh này");
        }

        // Kiểm tra xem thẻ/xe có session hoạt động nào không
        boolean cardHasActiveSession = parkingSessionRepository.existsByParkingCardParkingCardIdAndStatus(parkingCard.getParkingCardId(), ParkingSessionStatus.ACTIVE);
        if (cardHasActiveSession) {
            throw new ParkingSessionException("Thẻ gửi xe đã được sử dụng");
        }

        Vehicle vehicle = booking.getVehicle();
        boolean vehicleHasActiveSession = parkingSessionRepository.existsByVehicleVehiclesIdAndStatus(vehicle.getVehiclesId(), ParkingSessionStatus.ACTIVE);
        if (vehicleHasActiveSession) {
            throw new ParkingSessionException("Phương tiện này đã có một phiên gửi xe đang hoạt động");
        }

        // 3. Tạo parking session
        ParkingSession parkingSession = new ParkingSession();
        parkingSession.setParkingBranch(parkingBranch);
        parkingSession.setVehicle(vehicle);
        parkingSession.setParkingCard(parkingCard);
        parkingSession.setCheckInTime(now);
        parkingSession.setStatus(ParkingSessionStatus.ACTIVE);
        parkingSession = parkingSessionRepository.save(parkingSession);

        // 4. Cập nhật booking sang COMPLETED
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setParkingSession(parkingSession);
        booking.setCompletedAt(now);
        booking.setUpdatedAt(now);
        bookingRepository.save(booking);

        // 5. Cập nhật trạng thái thẻ sang IN_USE
        parkingCard.setStatus(ParkingCardStatus.IN_USE);
        parkingCardRepository.save(parkingCard);

        return convertToResponse(parkingSession);
    }
}


