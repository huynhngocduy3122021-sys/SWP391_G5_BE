package Parking.Controller;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import Parking.Model.*;
import Parking.Repository.*;
import Parking.Service.PaymentService;
import Parking.dto.request.SubmitMonthlyTicketRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/monthly-ticket-requests")
@RequiredArgsConstructor
@CrossOrigin("*")
@SecurityRequirement(name = "api_key")
public class MonthlyTicketRequestController {

    private final MonthlyTicketRequestRepository requestRepo;
    private final VehicleRepository vehicleRepo;
    private final UserRepository userRepo;
    private final PricePolicyRepository policyRepo;
    private final ParkingBranchRepository branchRepo;
    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> submitRequest(@RequestBody SubmitMonthlyTicketRequest req) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepo.findByUserEmail(username);
        if (user == null) user = userRepo.findByUserPhone(username);
        if (user == null) throw new RuntimeException("User not found");
        Vehicle vehicle = vehicleRepo.findById(req.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));
        PricePolicy policy = policyRepo.findById(req.getPolicyId())
                .orElseThrow(() -> new RuntimeException("Policy not found"));
        ParkingBranch branch = branchRepo.findById(req.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        MonthlyTicketRequest mtr = new MonthlyTicketRequest();
        mtr.setUser(user);
        mtr.setVehicle(vehicle);
        mtr.setPricePolicy(policy);
        mtr.setParkingBranch(branch);
        mtr.setStatus(0); // Pending
        mtr.setCreatedAt(LocalDateTime.now());
        
        return ResponseEntity.ok(requestRepo.save(mtr));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<MonthlyTicketRequest>> getAllRequests() {
        return ResponseEntity.ok(requestRepo.findAll());
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<MonthlyTicketRequest>> getMyRequests() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepo.findByUserEmail(username);
        if (user == null) user = userRepo.findByUserPhone(username);
        if (user == null) throw new RuntimeException("User not found");
        return ResponseEntity.ok(requestRepo.findByUserUserId(user.getUserId()));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        MonthlyTicketRequest req = requestRepo.findById(id).orElseThrow();
        req.setStatus(status);
        return ResponseEntity.ok(requestRepo.save(req));
    }

    @PostMapping("/{id}/payment")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<java.util.Map<String, String>> createMonthlyTicketPayment(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        String paymentUrl = paymentService.createMonthlyTicketPayment(id, clientIp);
        return ResponseEntity.ok(java.util.Map.of("paymentUrl", paymentUrl));
    }
}
