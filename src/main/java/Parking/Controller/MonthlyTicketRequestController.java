package Parking.Controller;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import Parking.Service.PaymentService;
import Parking.Service.MonthlyTicketRequestService;
import Parking.enums.MonthlyTicketRequestStatus;
import Parking.dto.request.ApproveMonthlyTicketRequest;
import Parking.dto.request.SubmitMonthlyTicketRequest;
import Parking.dto.response.MonthlyTicketRequestResponse;
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
    public ResponseEntity<MonthlyTicketRequestResponse> submitRequest(@Valid @RequestBody SubmitMonthlyTicketRequest req) {
        return ResponseEntity.ok(requestService.toResponse(requestService.submitRequest(req)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<MonthlyTicketRequestResponse>> getAllRequests() {
        return ResponseEntity.ok(requestService.getAllRequests());
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<MonthlyTicketRequestResponse>> getMyRequests() {
        return ResponseEntity.ok(requestService.getMyRequests());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MonthlyTicketRequestResponse> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return ResponseEntity.ok(requestService.toResponse(
                requestService.updateStatus(id, MonthlyTicketRequestStatus.fromCode(status))));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MonthlyTicketRequestResponse> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApproveMonthlyTicketRequest request) {
        return ResponseEntity.ok(requestService.toResponse(
                requestService.approveRequest(id, request.getParkingCardId())));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MonthlyTicketRequestResponse> reject(@PathVariable Long id) {
        return ResponseEntity.ok(requestService.toResponse(
                requestService.updateStatus(id, MonthlyTicketRequestStatus.REJECTED)));
    }

    @PostMapping("/{id}/payment")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> createPayment(@PathVariable Long id, HttpServletRequest request) {
        String clientIp = clientIpResolver.resolveIp(request);
        String paymentUrl = paymentService.createMonthlyTicketPayment(id, clientIp);
        return ResponseEntity.ok(Map.of("paymentUrl", paymentUrl));
    }
}
