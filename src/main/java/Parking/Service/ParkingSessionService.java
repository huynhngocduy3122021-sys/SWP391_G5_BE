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
import Parking.dto.request.GuestCheckInRequest;
import Parking.dto.request.GuestCheckOutRequest;
import Parking.dto.response.ParkingSessionResponse;
import Parking.dto.response.UserResponse;
import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingSessionStatus;
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
import java.util.List;


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

    private final PricePolicyRepository pricePolicyRepository;

    private final PaymentRepository paymentRepository;

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
            //b10 : trang thay the
            parkingCard.setStatus(ParkingCardStatus.IN_USE);

            parkingCardRepository.save(parkingCard);

            return convertToResponse(parkingSession);

        }
    public ParkingSessionResponse guestCheckOut(GuestCheckOutRequest request) { // hàm check out
       
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
        //b3: kiem tra chua thanh toan
        boolean paymentExists = paymentRepository.existsByParkingSessionParkingSessionId(parkingSession.getParkingSessionId());

        if (paymentExists) {
            throw new ParkingSessionException("Parking session has already been paid");
        }

        //b4 : chinh sach tinh gia
        Long vehicleTypeId =
                parkingSession
                    .getVehicle()
                    .getVehicleType()
                    .getVehicleTypeId();

        PricePolicy pricePolicy = pricePolicyRepository.findFirstByVehicleTypeVehicleTypeIdAndActiveTrueOrderByPricePolicyIdDesc(vehicleTypeId)
                    .orElseThrow(() -> new ParkingSessionException("Active price policy not found" ));

        LocalDateTime checkOutTime = LocalDateTime.now();
        // b5: tinh phi
         BigDecimal totalAmount = caculateParkingFee(parkingSession.getCheckInTime(),checkOutTime,pricePolicy);

         // b6 : hoan tat session

         parkingSession.setCheckOutTime(checkOutTime);

        parkingSession.setTotalAmount(totalAmount);

        parkingSession.setStatus( ParkingSessionStatus.COMPLETED );
        // b7 tao payment
        Payment payment = new Payment();

        payment.setAmount(totalAmount);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setPaidAt(checkOutTime);
        payment.setParkingSession( parkingSession );
        // dong bo 2 chieu
         parkingSession.setPayment(payment);

         // b8 : tra the ve AVAILABLE
        ParkingCard parkingCard = parkingSession.getParkingCard();

        parkingCard.setStatus(ParkingCardStatus.AVAILABLE);

        // parking sesion có cascade ALL vs payment , nên save sesion sẽ lưu luôn payment

        parkingSessionRepository.save(parkingSession);

        parkingCardRepository.save( parkingCard);
        return convertToResponse(parkingSession);
    }


    public List<ParkingSessionResponse> getAllParkingSession() {
        List<ParkingSession> parkingSessions = parkingSessionRepository.findAll();
        return parkingSessions.stream()
                    .map(this::convertToResponse) // chuyển đổi từng ParkingSession thành ParkingSessionReponse
                    .collect(Collectors.toList()); // thu thập kết quả vào một List<ParkingSessionReponse> và trả về
    }
    // hàm tính chi phí gửi xe
    private BigDecimal caculateParkingFee( LocalDateTime checkInTime,LocalDateTime checkOutTime,PricePolicy pricePolicy
    ) {
        if (checkInTime == null) {
            throw new ParkingSessionException("Check-in time is missing");
        }

        if (pricePolicy.getBasePrice() == null|| pricePolicy.getExtraHourPrice() == null|| pricePolicy.getBaseDurationMinutes() == null) {

            throw new ParkingSessionException("Price policy is invalid");
        }

        if (pricePolicy.getBaseDurationMinutes() <= 0) {
            throw new ParkingSessionException("Base duration must be greater than zero");
        }

        if (pricePolicy.getBasePrice().compareTo(BigDecimal.ZERO) < 0|| pricePolicy.getExtraHourPrice().compareTo(BigDecimal.ZERO) < 0) {
                        throw new ParkingSessionException("Parking price cannot be negative");
        }

        long totalMinutes = Duration.between(checkInTime,checkOutTime ).toMinutes();

        totalMinutes = Math.max(totalMinutes, 0);

        /*
         * Nếu thời gian gửi nhỏ hơn hoặc bằng
         * baseDurationMinutes thì chỉ thu basePrice.
         */
        if (totalMinutes <= pricePolicy.getBaseDurationMinutes()) {

            return pricePolicy.getBasePrice().setScale(2,RoundingMode.HALF_UP);
        }

        /*
         * Phần thời gian vượt quá gói cơ bản.
         */
        long extraMinutes = totalMinutes - pricePolicy.getBaseDurationMinutes();

        /*
         * Làm tròn lên mỗi giờ.
         *
         * 1 phút  -> 1 giờ
         * 60 phút -> 1 giờ
         * 61 phút -> 2 giờ
         */
        long extraHours = (extraMinutes + 59) / 60;

        BigDecimal extraAmount = pricePolicy.getExtraHourPrice().multiply(BigDecimal.valueOf(extraHours));

        return pricePolicy.getBasePrice().add(extraAmount).setScale(2, RoundingMode.HALF_UP);
        }
    
    
    private ParkingSessionResponse convertToResponse(ParkingSession parkingSession) {
        Vehicle vehicle = parkingSession.getVehicle();

        VehicleType vehicleType = vehicle.getVehicleType();

        ParkingCard parkingCard = parkingSession.getParkingCard();

        ParkingBranch parkingBranch = parkingSession.getParkingBranch();

        Payment payment = parkingSession.getPayment();

        return ParkingSessionResponse.builder()
            .parkingSessionId(parkingSession.getParkingSessionId())
            .parkingBranchId(parkingBranch.getParkingBranchId())
            .parkingBranchName(parkingBranch.getBranchName())
            .vehicleId(vehicle.getVehiclesId())
            .licensePlate(vehicle.getLicensePlate())
            .vehicleColor(vehicle.getVehicleColor())
            .vehicleBrand(vehicle.getVehicleBrand())
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
}


