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

/**
 * Controller chịu trách nhiệm xử lý các API liên quan đến "Yêu cầu đăng ký vé tháng" (Monthly Ticket Request).
 */
@RestController
@RequestMapping("/api/monthly-ticket-requests") // Tiền tố URL chung cho tất cả API trong lớp này
@RequiredArgsConstructor // Lombok tự động tạo Constructor để inject (tiêm) các Bean (như Repository) được đánh dấu là final
@CrossOrigin("*") // Cho phép các domain khác (Frontend) có thể gọi API này mà không bị chặn lỗi CORS
@SecurityRequirement(name = "api_key") // Yêu cầu xác thực (ví dụ như Bearer token) khi test trên Swagger UI
public class MonthlyTicketRequestController {

    private final MonthlyTicketRequestService requestService;
    private final PaymentService paymentService;
    private final ClientIpResolver clientIpResolver;

    /**
     * API: Gửi một yêu cầu đăng ký vé tháng mới.
     * Quyền truy cập: Bất kỳ ai đã đăng nhập (USER, STAFF, MANAGER, ADMIN).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MonthlyTicketRequestResponse> submitRequest(@Valid @RequestBody SubmitMonthlyTicketRequest req) {
        return ResponseEntity.ok(requestService.toResponse(requestService.submitRequest(req)));
    }

    /**
     * API: Lấy danh sách TẤT CẢ các yêu cầu đăng ký vé tháng.
     * Quyền truy cập: Chỉ dành cho nhân viên nội bộ (STAFF, MANAGER, ADMIN).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<MonthlyTicketRequestResponse>> getAllRequests() {
        return ResponseEntity.ok(requestService.getAllRequests());
    }

    /**
     * API: Lấy danh sách các yêu cầu đăng ký vé tháng CỦA CÁ NHÂN người đang đăng nhập.
     * Quyền truy cập: Bất kỳ ai đã đăng nhập.
     */
    @GetMapping("/my-requests")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<MonthlyTicketRequestResponse>> getMyRequests() {
        return ResponseEntity.ok(requestService.getMyRequests());
    }

    /**
     * API: Cập nhật trạng thái của một yêu cầu đăng ký vé tháng (Ví dụ: Chuyển từ Chờ duyệt -> Đã duyệt).
     * Quyền truy cập: Chỉ nhân viên nội bộ (STAFF, MANAGER, ADMIN).
     */
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

    /**
     * API: Cho phép chính người dùng đã tạo yêu cầu tự hủy yêu cầu của mình
     * khi yêu cầu đang chờ thanh toán hoặc đang chờ Ban Quản Lý phê duyệt.
     * Quyền truy cập: Bất kỳ ai đã đăng nhập (USER tự hủy yêu cầu của chính mình).
     */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MonthlyTicketRequestResponse> cancelMyRequest(@PathVariable Long id) {
        return ResponseEntity.ok(requestService.toResponse(
                requestService.cancelMyRequest(id)));
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
