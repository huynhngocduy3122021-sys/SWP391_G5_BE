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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import Parking.Model.MonthlyTicketRequest;
import Parking.Model.Payment;
import Parking.Repository.MonthlyTicketRequestRepository;
import Parking.Repository.MonthlyTicketRepository;
import Parking.Repository.ParkingCardRepository;
import Parking.Repository.ParkingSessionRepository;
import Parking.Repository.PaymentRepository;
import Parking.Repository.PricePolicyRepository;
import Parking.enums.PaymentStatus;
import Parking.enums.MonthlyTicketRequestStatus;

@ExtendWith(MockitoExtension.class)
class PaymentServiceMonthlyTicketTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PricePolicyRepository pricePolicyRepository;
    @Mock private VnPayService vnPayService;
    @Mock private ParkingSessionRepository parkingSessionRepository;
    @Mock private ParkingCardRepository parkingCardRepository;
    @Mock private MonthlyTicketRepository monthlyTicketRepository;
    @Mock private MonthlyTicketRequestRepository monthlyTicketRequestRepository;

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
}
