package Parking.Controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import Parking.Service.IncidentImageService;
import Parking.Service.IncidentReportService;
import Parking.Service.LostMonthlyCardReplacementService;
import Parking.Service.PaymentService;
import Parking.dto.response.LostCardPaymentResponse;
import Parking.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class LostCardPaymentControllerTest {

    @Mock private IncidentReportService incidentReportService;
    @Mock private IncidentImageService incidentImageService;
    @Mock private LostMonthlyCardReplacementService replacementService;
    @Mock private PaymentService paymentService;
    @Mock private ClientIpResolver clientIpResolver;
    @Mock private HttpServletRequest servletRequest;

    @InjectMocks private IncidentReportController controller;

    @Test
    void createLostCardPayment_returnsCreated() {
        LostCardPaymentResponse response = LostCardPaymentResponse.builder()
                .incidentId(7L)
                .paymentId(11L)
                .build();
        when(clientIpResolver.resolveIp(servletRequest)).thenReturn("127.0.0.1");
        when(paymentService.createLostCardPayment(7L, "127.0.0.1"))
                .thenReturn(response);

        ResponseEntity<LostCardPaymentResponse> result =
                controller.createLostCardPayment(7L, servletRequest);

        assertEquals(201, result.getStatusCode().value());
        assertEquals(11L, result.getBody().getPaymentId());
    }
}
