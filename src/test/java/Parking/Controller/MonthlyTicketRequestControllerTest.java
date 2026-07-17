package Parking.Controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import Parking.Model.MonthlyTicketRequest;
import Parking.Service.MonthlyTicketRequestService;
import Parking.Service.PaymentService;
import Parking.enums.MonthlyTicketRequestStatus;
import Parking.web.ClientIpResolver;

@ExtendWith(MockitoExtension.class)
class MonthlyTicketRequestControllerTest {

    @Mock private MonthlyTicketRequestService requestService;
    @Mock private PaymentService paymentService;
    @Mock private ClientIpResolver clientIpResolver;

    @InjectMocks private MonthlyTicketRequestController controller;

    @Test
    void updateStatus_shouldConvertRejectedCodeAndDelegateToService() {
        MonthlyTicketRequest updated = new MonthlyTicketRequest();
        updated.setStatus(MonthlyTicketRequestStatus.REJECTED);
        when(requestService.updateStatus(32L, MonthlyTicketRequestStatus.REJECTED)).thenReturn(updated);

        ResponseEntity<MonthlyTicketRequest> response = controller.updateStatus(32L, -1);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MonthlyTicketRequestStatus.REJECTED, response.getBody().getStatus());
        verify(requestService).updateStatus(32L, MonthlyTicketRequestStatus.REJECTED);
    }

    @Test
    void updateStatus_shouldConvertApprovedCodeAndDelegateToService() {
        MonthlyTicketRequest updated = new MonthlyTicketRequest();
        updated.setStatus(MonthlyTicketRequestStatus.APPROVED);
        when(requestService.updateStatus(33L, MonthlyTicketRequestStatus.APPROVED)).thenReturn(updated);

        ResponseEntity<MonthlyTicketRequest> response = controller.updateStatus(33L, 2);

        assertEquals(MonthlyTicketRequestStatus.APPROVED, response.getBody().getStatus());
        verify(requestService).updateStatus(33L, MonthlyTicketRequestStatus.APPROVED);
    }
}
