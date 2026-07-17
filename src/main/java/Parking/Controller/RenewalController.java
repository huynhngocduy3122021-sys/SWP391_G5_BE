package Parking.Controller;

import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import Parking.Model.*;
import Parking.Repository.*;
import Parking.dto.request.CreateRenewalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/monthly-tickets")
@RequiredArgsConstructor
@CrossOrigin("*")
@SecurityRequirement(name = "api_key")
public class RenewalController {

    private final MonthlyTicketRepository ticketRepo;
    private final PricePolicyRepository policyRepo;
    private final ParkingBranchRepository branchRepo;
    private final MonthlyTicketRequestRepository requestRepo;
    private final UserRepository userRepo;

    @PostMapping("/{ticketId}/renewal-requests")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    @Transactional
    public ResponseEntity<?> createRenewalRequest(
            @PathVariable Long ticketId,
            @RequestBody CreateRenewalRequest dto) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepo.findByUserEmail(username);
        if (currentUser == null) currentUser = userRepo.findByUserPhone(username);
        if (currentUser == null) throw new RuntimeException("User not found");

        MonthlyTicket ticket = ticketRepo.findById(ticketId)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy vé tháng"));

        if (!ticket.getVehicle().getUser().getUserId().equals(currentUser.getUserId())) {
            return ResponseEntity.status(403).body("Bạn không sở hữu vé tháng này");
        }

        PricePolicy policy = policyRepo.findById(dto.getPolicyId())
            .orElseThrow(() -> new RuntimeException("Không tìm thấy gói dịch vụ"));

        PricePolicy currentPolicy = ticket.getPricePolicy();
        if (currentPolicy == null) {
            MonthlyTicketRequest issuedRequest = ticket.getMonthlyTicketRequest();
            if (issuedRequest == null) {
                issuedRequest = requestRepo.findBestIssuedRequestForTicket(
                    ticket.getVehicle().getVehiclesId(),
                    ticket.getParkingCard().getParkingBranch().getParkingBranchId(),
                    ticket.getCreatedAt()
                ).orElse(null);
            }

            if (issuedRequest != null && issuedRequest.getPricePolicy() != null) {
                currentPolicy = issuedRequest.getPricePolicy();
                // Backfill vé cũ để các lần đọc/gia hạn sau có nguồn dữ liệu rõ ràng.
                ticket.setPricePolicy(currentPolicy);
                ticketRepo.save(ticket);
            }
        }

        if (currentPolicy == null || currentPolicy.getPricePolicyId() == null) {
            return ResponseEntity.badRequest().body(
                "Không xác định được gói hiện tại của vé. Vui lòng liên hệ quản lý để kiểm tra"
            );
        }

        if (!currentPolicy.getPricePolicyId().equals(policy.getPricePolicyId())) {
            return ResponseEntity.badRequest().body(
                "Chỉ được gia hạn đúng gói dịch vụ hiện tại: " + currentPolicy.getPolicyName()
            );
        }

        if (!policy.isActive()) {
            return ResponseEntity.badRequest().body("Gói dịch vụ hiện tại đã ngừng hoạt động");
        }

        ParkingBranch branch = branchRepo.findById(dto.getBranchId())
            .orElseThrow(() -> new RuntimeException("Không tìm thấy chi nhánh"));

        Vehicle vehicle = ticket.getVehicle();

        if (vehicle.getVehicleType() == null || policy.getVehicleType() == null ||
            !vehicle.getVehicleType().getVehicleTypeId().equals(policy.getVehicleType().getVehicleTypeId())) {
            return ResponseEntity.badRequest().body("Không thể gia hạn bằng gói của loại phương tiện khác");
        }

        MonthlyTicketRequest request = new MonthlyTicketRequest();
        request.setVehicle(vehicle);
        request.setPricePolicy(policy);
        request.setParkingBranch(branch);
        request.setUser(currentUser);
        request.setStatus(0); // PENDING_PAYMENT
        request.setCreatedAt(LocalDateTime.now());
        request.setRenewalOfTicket(ticket);

        return ResponseEntity.ok(requestRepo.save(request));
    }
}
