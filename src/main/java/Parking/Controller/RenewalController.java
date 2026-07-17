package Parking.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import Parking.Service.MonthlyTicketRenewalService;
import Parking.dto.request.CreateRenewalRequest;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/monthly-tickets")
@RequiredArgsConstructor
@CrossOrigin("*")
@SecurityRequirement(name = "api_key")
public class RenewalController {

    private final MonthlyTicketRenewalService renewalService;

    @PostMapping("/{ticketId}/renewal-requests")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> createRenewalRequest(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateRenewalRequest dto) {

        return ResponseEntity.ok(renewalService.createRenewalRequest(ticketId, dto));
    }
}
