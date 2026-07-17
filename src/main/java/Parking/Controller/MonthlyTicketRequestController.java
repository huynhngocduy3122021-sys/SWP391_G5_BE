package Parking.Controller;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import Parking.Model.MonthlyTicketRequest;
import Parking.Service.PaymentService;
import Parking.Service.MonthlyTicketRequestService;
import Parking.enums.MonthlyTicketRequestStatus;
import Parking.dto.request.SubmitMonthlyTicketRequest;
import Parking.web.ClientIpResolver;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/monthly-ticket-requests")
@RequiredArgsConstructor
@CrossOrigin("*")
@SecurityRequirement(name = "api_key")
public class MonthlyTicketRequestController {

    private final MonthlyTicketRequestService requestService;
    private final PaymentService paymentService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MonthlyTicketRequest> submitRequest(@Valid @RequestBody SubmitMonthlyTicketRequest req) {
        return ResponseEntity.ok(requestService.submitRequest(req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<MonthlyTicketRequest>> getAllRequests() {
        return ResponseEntity.ok(requestService.getAllRequests());
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<MonthlyTicketRequest>> getMyRequests() {
        return ResponseEntity.ok(requestService.getMyRequests());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MonthlyTicketRequest> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return ResponseEntity.ok(requestService.updateStatus(id, MonthlyTicketRequestStatus.fromCode(status)));
    }

    @PostMapping("/{id}/payment")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> createPayment(@PathVariable Long id, HttpServletRequest request) {
        String clientIp = clientIpResolver.resolveIp(request);
        String paymentUrl = paymentService.createMonthlyTicketPayment(id, clientIp);
        return ResponseEntity.ok(Map.of("paymentUrl", paymentUrl));
    }
}
