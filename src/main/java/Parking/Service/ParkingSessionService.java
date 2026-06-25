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
import Parking.dto.response.GuestCheckOutResponse;
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
            return paymentService.processCheckOutPayment(parkingSession, request.getPaymentMethod(), clientIp);
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


