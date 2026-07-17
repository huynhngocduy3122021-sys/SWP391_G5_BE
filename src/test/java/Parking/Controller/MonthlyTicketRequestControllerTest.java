package Parking.Controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import Parking.Model.MonthlyTicketRequest;
import Parking.Model.MonthlyTicket;
import Parking.Model.Payment;
import Parking.Model.PricePolicy;
import Parking.Model.Vehicle;
import Parking.Model.VehicleType;
import Parking.Repository.MonthlyTicketRepository;
import Parking.Repository.MonthlyTicketRequestRepository;
import Parking.Repository.ParkingBranchRepository;
import Parking.Repository.ParkingCardRepository;
import Parking.Repository.PricePolicyRepository;
import Parking.Repository.UserRepository;
import Parking.Repository.VehicleRepository;
import Parking.Service.PaymentService;
import Parking.enums.PaymentStatus;

@ExtendWith(MockitoExtension.class)
class MonthlyTicketRequestControllerTest {

    @Mock private MonthlyTicketRequestRepository requestRepo;
    @Mock private VehicleRepository vehicleRepo;
    @Mock private UserRepository userRepo;
    @Mock private PricePolicyRepository policyRepo;
    @Mock private ParkingBranchRepository branchRepo;
    @Mock private MonthlyTicketRepository monthlyTicketRepo;
    @Mock private ParkingCardRepository parkingCardRepo;
    @Mock private PaymentService paymentService;

    @InjectMocks private MonthlyTicketRequestController controller;

    @Test
    void reject_shouldAllowLegacyApprovedPaidRequestWhenNoTicketWasIssuedFromIt() {
        MonthlyTicketRequest request = approvedPaidRequest(32L);

        when(requestRepo.findById(32L)).thenReturn(Optional.of(request));
        when(monthlyTicketRepo.existsByMonthlyTicketRequestId(32L)).thenReturn(false);
        when(requestRepo.save(any(MonthlyTicketRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = controller.updateStatus(32L, -1);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(-1, request.getStatus());
        verify(requestRepo).save(request);
    }

    @Test
    void reject_shouldBlockOnlyWhenTicketWasIssuedFromThisRequest() {
        MonthlyTicketRequest request = approvedPaidRequest(32L);

        when(requestRepo.findById(32L)).thenReturn(Optional.of(request));
        when(monthlyTicketRepo.existsByMonthlyTicketRequestId(32L)).thenReturn(true);

        ResponseEntity<?> response = controller.updateStatus(32L, -1);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Không thể từ chối yêu cầu đã cấp vé.", response.getBody());
        assertEquals(2, request.getStatus());
        verify(requestRepo, never()).save(any(MonthlyTicketRequest.class));
    }

    @Test
    void approveRenewal_shouldRejectWhenRequestedPolicyDiffersFromCurrentPolicy() {
        VehicleType vehicleType = new VehicleType();
        vehicleType.setVehicleTypeId(3L);

        Vehicle vehicle = new Vehicle();
        vehicle.setVehiclesId(80L);
        vehicle.setVehicleType(vehicleType);

        PricePolicy currentPolicy = new PricePolicy();
        currentPolicy.setPricePolicyId(7L);
        currentPolicy.setPolicyName("[Gói Tháng] Ô tô");
        currentPolicy.setVehicleType(vehicleType);

        PricePolicy requestedPolicy = new PricePolicy();
        requestedPolicy.setPricePolicyId(8L);
        requestedPolicy.setPolicyName("[Gói VIP] Ô tô");
        requestedPolicy.setVehicleType(vehicleType);

        MonthlyTicket oldTicket = new MonthlyTicket();
        oldTicket.setVehicle(vehicle);
        oldTicket.setPricePolicy(currentPolicy);

        MonthlyTicketRequest request = new MonthlyTicketRequest();
        request.setId(33L);
        request.setStatus(1);
        request.setVehicle(vehicle);
        request.setPricePolicy(requestedPolicy);
        request.setRenewalOfTicket(oldTicket);

        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.PAID);
        request.setPayment(payment);

        when(requestRepo.findById(33L)).thenReturn(Optional.of(request));

        ResponseEntity<?> response = controller.updateStatus(33L, 2);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(
                "Không thể duyệt: gói gia hạn không trùng với gói hiện tại của vé.",
                response.getBody()
        );
        verify(requestRepo, never()).save(any(MonthlyTicketRequest.class));
    }

    private MonthlyTicketRequest approvedPaidRequest(Long id) {
        MonthlyTicketRequest request = new MonthlyTicketRequest();
        request.setId(id);
        request.setStatus(2);

        Payment payment = new Payment();
        payment.setAmount(BigDecimal.valueOf(300000));
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setMonthlyTicketRequest(request);
        request.setPayment(payment);
        return request;
    }
}
