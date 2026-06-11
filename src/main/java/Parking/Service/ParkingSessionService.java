package Parking.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import javax.xml.datatype.DatatypeConfigurationException;

import org.hibernate.validator.constraints.time.DurationMax;
import org.springframework.boot.autoconfigure.jms.JmsProperties.Listener.Session;
import org.springframework.stereotype.Service;

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
     private final VehicleRepository vehicleRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final ParkingZoneRepository parkingZoneRepository;
    private final ParkingCardRepository parkingCardRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final PricePolicyRepository pricePolicyRepository;
    private final PaymentRepository paymentRepository;

    public ParkingSessionResponse guestCheckIn(GuestCheckInRequest request) { // hàm tạo guest check in
        
        
        String licensePlate = normalizeLicensePlate(request.getLicensePlate());
        
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                                    .orElseThrow(() -> new ParkingSessionException("Vehicle type not found"));

        ParkingZone parkingZone = parkingZoneRepository.findById(request.getParkingZoneId())
                                .orElseThrow(() -> new ParkingSessionException("Parking zone not found"));
        if(!parkingZone.isActive()) {
            throw new ParkingSessionException("Parking zone is not active");
        }
        if(parkingZone.getCurrentCapacity() >= parkingZone.getMaxCapacity()) {
            throw new ParkingSessionException("Parking zone is full");
        }

        if(parkingZone.getVehicleType() != null && !parkingZone.getVehicleType().getVehicleTypeId().equals(vehicleType.getVehicleTypeId())) {
            throw new ParkingSessionException("Vehicle type is not allow in this zone");
        }

        ParkingCard parkingCard = parkingCardRepository.findByCardCode(request.getCardCode())
                                .orElseThrow(() -> new ParkingSessionException("Parking card not found"));
        
        if(parkingCard.getStatus() != ParkingCardStatus.AVAILABLE){
            throw new ParkingSessionException("Parking card not available");
        }

        Vehicle vehicle = vehicleRepository.findByLicensePlate(licensePlate)
                        .orElseGet(() ->{
                            Vehicle newVehicle = new Vehicle();
                            newVehicle.setLicensePlate(licensePlate);
                            newVehicle.setVehicleType(vehicleType);
                            newVehicle.setVehicleSource(VehicleSource.GUEST);
                            return vehicleRepository.save(newVehicle);
                        });
        boolean vehicleAlreadyInParking = parkingSessionRepository.existsByVehicleAndStatus(vehicle,ParkingSessionStatus.ACTIVE);

        if(vehicleAlreadyInParking) {
            throw new ParkingSessionException("This vehicle is already in parking");
        }

        ParkingSession session = new ParkingSession();
        session.setVehicle(vehicle);
        session.setParkingCard(parkingCard);
        session.setParkingZone(parkingZone);
        session.setCheckInTime(LocalDateTime.now());
        session.setStatus(ParkingSessionStatus.ACTIVE);

        ParkingSession saveSession = parkingSessionRepository.save(session);

        parkingCard.setStatus(ParkingCardStatus.IN_USE);
        parkingCardRepository.save(parkingCard);

        parkingZone.setCurrentCapacity(parkingZone.getCurrentCapacity() + 1);
        parkingZoneRepository.save(parkingZone);
        return convertToResponse(saveSession);

    }
    public ParkingSessionResponse guestCheckOut(GuestCheckOutRequest request) { // hàm check out
        ParkingCard parkingCard = parkingCardRepository.findByCardCode(request.getCardCode())
                                .orElseThrow(() -> new ParkingSessionException("Parking card not found!"));
        ParkingSession parkingSession = parkingSessionRepository.findByParkingCardAndStatus(parkingCard, ParkingSessionStatus.ACTIVE)
                                    .orElseThrow(() -> new ParkingSessionException("Active parking session not found!"));

        LocalDateTime checkOutTime = LocalDateTime.now();
        BigDecimal totalAmount = caculateParkingFee(parkingSession, checkOutTime);
        Payment payment = new Payment();
        payment.setAmount(totalAmount);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setPaidAt(checkOutTime);
        payment.setParkingSession(parkingSession);

        Payment savePayment = paymentRepository.save(payment);

        parkingSession.setCheckOutTime(checkOutTime);
        parkingSession.setTotalAmount(totalAmount);
        parkingSession.setStatus(ParkingSessionStatus.COMPLETED);
        parkingSession.setPayment(savePayment);

        ParkingSession saveParkingSession = parkingSessionRepository.save(parkingSession);

        parkingCard.setStatus(ParkingCardStatus.AVAILABLE);
        parkingCardRepository.save(parkingCard);

        ParkingZone parkingZone = parkingSession.getParkingZone();
        parkingZone.setCurrentCapacity(Math.max(0, parkingZone.getCurrentCapacity() - 1));
        parkingZoneRepository.save(parkingZone);
        return convertToResponse(saveParkingSession);


    }

    public List<ParkingSessionResponse> getAllParkingSession() {
        List<ParkingSession> parkingSessions = parkingSessionRepository.findAll();
        return parkingSessions.stream()
                    .map(this::convertToResponse) // chuyển đổi từng ParkingSession thành ParkingSessionReponse
                    .collect(Collectors.toList()); // thu thập kết quả vào một List<ParkingSessionReponse> và trả về
    }

    private BigDecimal caculateParkingFee(ParkingSession session , LocalDateTime checkOuDateTime) { // hàm tính chi phí gửi xe
        VehicleType vehicleType = session.getVehicle().getVehicleType();

        PricePolicy pricePolicy = pricePolicyRepository.findFirstByVehicleTypeAndActiveTrue(vehicleType)
                            .orElseThrow(() -> new ParkingSessionException("Price policy not found!"));
        long totalMinutes = Duration.between(session.getCheckInTime(), checkOuDateTime).toMinutes();

        if(totalMinutes <= 0) {
            totalMinutes = 1;
        }
        BigDecimal basePrice = pricePolicy.getBasePrice();
        int baseDurationMinutes = pricePolicy.getBaseDurationMinutes();
        BigDecimal extraHourPrice = pricePolicy.getExtraHourPrice();

        if(totalMinutes <= baseDurationMinutes) {
            return basePrice;
        }
        long extraMinutes = totalMinutes - baseDurationMinutes;
        long extraHours = (long) Math.ceil(extraMinutes/60.0);

        return basePrice.add(extraHourPrice.multiply(BigDecimal.valueOf(extraHours)));
    }
    private String normalizeLicensePlate(String licensePlate) {
        if(licensePlate == null || licensePlate.isBlank()) {
            throw new ParkingSessionException("License plate is required");
        }
        return licensePlate.trim().toUpperCase().replace(" ", "");
    }
    private ParkingSessionResponse convertToResponse(ParkingSession session) {
        ParkingSessionResponse response = new ParkingSessionResponse();

        response.setParkingSessionId(session.getParkingSessionId());
        response.setLicensePlate(session.getVehicle().getLicensePlate());
        response.setVehicleType(session.getVehicle().getVehicleType().getTypeName());
        response.setParkingZone(session.getParkingZone().getZoneName());
        response.setCardCode(session.getParkingCard().getCardCode());
        response.setCheckInTime(session.getCheckInTime());
        response.setCheckOutTime(session.getCheckOutTime());
        response.setStatus(session.getStatus().name());
        response.setTotalAmount(session.getTotalAmount());
        
        return response;
    }
}


