package Parking.Controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import Parking.Model.*;
import Parking.Repository.*;
import Parking.Service.PaymentService;
import Parking.enums.ParkingCardStatus;
import Parking.enums.PaymentStatus;
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
    private final MonthlyTicketRepository monthlyTicketRepo;
    private final ParkingCardRepository parkingCardRepo;
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

        if (vehicle.getUser() == null || !vehicle.getUser().getUserId().equals(user.getUserId())) {
            return ResponseEntity.status(403).body(Map.of("message", "Phương tiện không thuộc tài khoản hiện tại"));
        }

        if (vehicle.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phương tiện đã bị xóa"));
        }

        if (!policy.isActive()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Gói dịch vụ đã ngừng hoạt động"));
        }

        if (vehicle.getVehicleType() == null || policy.getVehicleType() == null ||
            !vehicle.getVehicleType().getVehicleTypeId().equals(policy.getVehicleType().getVehicleTypeId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Loại phương tiện không phù hợp với gói dịch vụ"));
        }

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
    @Transactional
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        MonthlyTicketRequest req = requestRepo.findById(id).orElseThrow();
        
        if (status == 2) { // Approved
            if (req.getStatus() != 1) { // PENDING_APPROVAL
                return ResponseEntity.badRequest().body("Yêu cầu không ở trạng thái chờ duyệt.");
            }
            
            Vehicle vehicle = req.getVehicle();
            PricePolicy policy = req.getPricePolicy();
            if (vehicle.getVehicleType() == null || policy.getVehicleType() == null ||
                !vehicle.getVehicleType().getVehicleTypeId().equals(policy.getVehicleType().getVehicleTypeId())) {
                return ResponseEntity.badRequest().body("Dữ liệu yêu cầu không hợp lệ: loại xe không khớp gói.");
            }

            Payment payment = req.getPayment();
            if (payment == null || payment.getPaymentStatus() != PaymentStatus.PAID) {
                return ResponseEntity.badRequest().body("Yêu cầu này chưa được thanh toán thành công.");
            }
            
            MonthlyTicket oldTicket = req.getRenewalOfTicket();
            if (oldTicket != null) {
                if (!oldTicket.getVehicle().getVehiclesId().equals(req.getVehicle().getVehiclesId())) {
                    return ResponseEntity.badRequest().body("Xe gia hạn không khớp vé hiện tại");
                }

                PricePolicy currentPolicy = oldTicket.getPricePolicy();
                if (currentPolicy == null || currentPolicy.getPricePolicyId() == null) {
                    return ResponseEntity.badRequest().body(
                        "Không xác định được gói hiện tại của vé. Không thể duyệt gia hạn."
                    );
                }

                if (!currentPolicy.getPricePolicyId().equals(policy.getPricePolicyId())) {
                    return ResponseEntity.badRequest().body(
                        "Không thể duyệt: gói gia hạn không trùng với gói hiện tại của vé."
                    );
                }
            } else {
                oldTicket = monthlyTicketRepo.findLatestTicketByVehicle(req.getVehicle().getVehiclesId()).orElse(null);
            }
            
            ParkingCard card = null;
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startDate = now;
            LocalDateTime endDate = now.plusMonths(1);
            
            if (oldTicket != null) {
                oldTicket.setStatus(0); // Deactivate old ticket
                monthlyTicketRepo.save(oldTicket);
                
                card = oldTicket.getParkingCard();
                // Nếu thẻ cũ chưa hết hạn, hạn dùng mới sẽ cộng dồn thêm 1 tháng kể từ ngày hết hạn của thẻ cũ
                if (oldTicket.getEndDate().isAfter(now)) {
                    endDate = oldTicket.getEndDate().plusMonths(1);
                }
            } else {
                // First-time purchase: tìm một thẻ giữ xe loại tháng khả dụng tại chi nhánh
                card = parkingCardRepo.findFirstAvailableMonthlyCard(req.getParkingBranch().getParkingBranchId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy thẻ giữ xe loại tháng còn trống tại chi nhánh " + req.getParkingBranch().getBranchName()));
            }
            
            // Đánh dấu thẻ là đang sử dụng (IN_USE)
            card.setStatus(ParkingCardStatus.IN_USE);
            parkingCardRepo.save(card);
            
            // Tạo vé tháng mới
            MonthlyTicket newTicket = new MonthlyTicket();
            newTicket.setVehicle(req.getVehicle());
            newTicket.setParkingCard(card);
            newTicket.setStartDate(startDate);
            newTicket.setEndDate(endDate);
            newTicket.setStatus(1); // Active
            newTicket.setGuestName(req.getUser().getUserFullName());
            newTicket.setGuestPhone(req.getUser().getUserPhone());
            newTicket.setMonthlyTicketRequest(req);
            newTicket.setPricePolicy(req.getPricePolicy());
            
            monthlyTicketRepo.save(newTicket);
        } else if (status == -1) { // Rejected
            boolean ticketIssuedFromThisRequest =
                    monthlyTicketRepo.existsByMonthlyTicketRequestId(id);

            if (ticketIssuedFromThisRequest) {
                return ResponseEntity.badRequest().body("Không thể từ chối yêu cầu đã cấp vé.");
            }

            // Dữ liệu cũ có thể có trạng thái APPROVED dù chưa thực sự cấp vé.
            // Payment PAID không đồng nghĩa với việc vé đã được phát hành.
            if (req.getStatus() == 2
                    && req.getPayment() != null
                    && req.getPayment().getPaymentStatus() == PaymentStatus.PAID) {
                req.setStatus(1); // Chuẩn hóa về PENDING_APPROVAL trước khi reject.
            }
        }
        
        req.setStatus(status);
        return ResponseEntity.ok(requestRepo.save(req));
    }

    @PostMapping("/{id}/payment")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> createPayment(@PathVariable Long id, HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        String paymentUrl = paymentService.createMonthlyTicketPayment(id, clientIp);
        return ResponseEntity.ok(Map.of("paymentUrl", paymentUrl));
    }
}
