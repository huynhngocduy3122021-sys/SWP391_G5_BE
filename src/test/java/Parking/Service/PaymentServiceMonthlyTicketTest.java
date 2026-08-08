package Parking.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import Parking.Model.MonthlyTicketRequest;
import Parking.Model.Payment;
import Parking.Model.ParkingBranch;
import Parking.Model.ParkingSession;
import Parking.Model.User;
import Parking.Repository.MonthlyTicketRequestRepository;
import Parking.Repository.IncidentReportRepository;
import Parking.Repository.MonthlyTicketRepository;
import Parking.Repository.ParkingCardRepository;
import Parking.Repository.ParkingSessionRepository;
import Parking.Repository.PaymentRepository;
import Parking.Repository.PricePolicyRepository;
import Parking.enums.PaymentStatus;
import Parking.enums.MonthlyTicketRequestStatus;
import Parking.enums.UserRole;

@ExtendWith(MockitoExtension.class)
class PaymentServiceMonthlyTicketTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PricePolicyRepository pricePolicyRepository;
    @Mock private VnPayService vnPayService;
    @Mock private ParkingSessionRepository parkingSessionRepository;
    @Mock private ParkingCardRepository parkingCardRepository;
    @Mock private MonthlyTicketRepository monthlyTicketRepository;
    @Mock private MonthlyTicketRequestRepository monthlyTicketRequestRepository;
    @Mock private IncidentReportRepository incidentReportRepository;
    @Mock private BranchScopeService branchScopeService;
    @Mock private CurrentUserService currentUserService;

    @InjectMocks private PaymentService paymentService;

    @Test
    void handleVnPayCallback_shouldMoveMonthlyTicketRequestToPendingApprovalAfterSuccessfulPayment() {
        MonthlyTicketRequest request = new MonthlyTicketRequest();
        request.setId(10L);
        request.setStatus(MonthlyTicketRequestStatus.PENDING_PAYMENT);

        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setMonthlyTicketRequest(request);
        payment.setAmount(BigDecimal.valueOf(500000));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "TXN_1");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "VNP123");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_SecureHash", "hash");

        when(vnPayService.verifySignature(any(Map.class))).thenReturn(true);
        when(vnPayService.isCorrectTmnCode(any(Map.class))).thenReturn(true);
        when(paymentRepository.findByTransactionRefForUpdate("TXN_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(monthlyTicketRequestRepository.save(any(MonthlyTicketRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.handleVnPayCallback(params);

        assertEquals(MonthlyTicketRequestStatus.PENDING_APPROVAL, request.getStatus());
        verify(monthlyTicketRequestRepository).save(request);
    }

    @Test
    void getAllPaymentsForReport_staffShouldOnlySeeSessionPaymentsInAssignedBranch() {
        ParkingBranch staffBranch = branch(1L);
        User staff = new User();
        staff.setUserRole(UserRole.STAFF);
        staff.setParkingBranch(staffBranch);

        Payment ownBranchSessionPayment = sessionPayment(1L, staffBranch);
        Payment otherBranchSessionPayment = sessionPayment(2L, branch(2L));

        Payment monthlyTicketPayment = new Payment();
        monthlyTicketPayment.setPaymentId(3L);
        monthlyTicketPayment.setMonthlyTicketRequest(new MonthlyTicketRequest());

        when(currentUserService.getCurrentUser()).thenReturn(staff);
        when(paymentRepository.findAll()).thenReturn(List.of(
                ownBranchSessionPayment,
                otherBranchSessionPayment,
                monthlyTicketPayment));

        var result = paymentService.getAllPaymentsForReport();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getPaymentId());
        assertEquals(1L, result.get(0).getParkingSessionId());
    }

    private ParkingBranch branch(Long id) {
        ParkingBranch branch = new ParkingBranch();
        branch.setParkingBranchId(id);
        return branch;
    }

    private Payment sessionPayment(Long id, ParkingBranch branch) {
        ParkingSession session = new ParkingSession();
        session.setParkingSessionId(id);
        session.setParkingBranch(branch);

        Payment payment = new Payment();
        payment.setPaymentId(id);
        payment.setParkingSession(session);
        payment.setAmount(BigDecimal.valueOf(10_000));
        return payment;
    }
}
