package Parking.Controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import Parking.Service.IncidentImageService;
import Parking.Service.IncidentReportService;
import Parking.Service.LostMonthlyCardReplacementService;
import Parking.dto.request.LostCardIncidentRequest;
import Parking.dto.response.IncidentReportResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class IncidentReportControllerTest {

    @Mock private IncidentReportService incidentReportService;
    @Mock private IncidentImageService incidentImageService;
    @Mock private LostMonthlyCardReplacementService lostMonthlyCardReplacementService;

    @InjectMocks private IncidentReportController controller;

    @Test
    void reportLostCard_returnsCreated() {
        LostCardIncidentRequest request = new LostCardIncidentRequest();
        request.setDescription("Mất thẻ trước cổng");
        request.setParkingCardId(10L);
        request.setParkingBranchId(2L);

        IncidentReportResponse response = IncidentReportResponse.builder().incidentId(99L).build();
        when(incidentReportService.reportLostCard(request)).thenReturn(response);

        ResponseEntity<IncidentReportResponse> result = controller.reportLostCard(request);

        assertEquals(201, result.getStatusCode().value());
        assertEquals(99L, result.getBody().getIncidentId());
    }
}
