package Parking.Controller;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import Parking.Model.*;
import Parking.Repository.*;
import Parking.dto.request.SubmitMonthlyTicketRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
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

    // Các Repository dùng để truy vấn/thao tác dữ liệu với các bảng trong cơ sở dữ liệu
    private final MonthlyTicketRequestRepository requestRepo; // Thao tác với bảng Yêu cầu vé tháng
    private final VehicleRepository vehicleRepo;              // Thao tác với bảng Xe
    private final UserRepository userRepo;                    // Thao tác với bảng Người dùng (Khách hàng/Nhân viên)
    private final PricePolicyRepository policyRepo;           // Thao tác với bảng Chính sách giá
    private final ParkingBranchRepository branchRepo;         // Thao tác với bảng Chi nhánh bãi đỗ

    /**
     * API: Gửi một yêu cầu đăng ký vé tháng mới.
     * Quyền truy cập: Bất kỳ ai đã đăng nhập (USER, STAFF, MANAGER, ADMIN).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> submitRequest(@RequestBody SubmitMonthlyTicketRequest req) {
        // 1. Lấy thông tin định danh (username) của người dùng đang đăng nhập từ Security Context (thường là email hoặc SĐT)
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        
        // 2. Tìm kiếm đối tượng User trong cơ sở dữ liệu dựa theo username
        User user = userRepo.findByUserEmail(username); // Thử tìm theo Email trước
        if (user == null) user = userRepo.findByUserPhone(username); // Nếu không có, thử tìm theo số điện thoại
        if (user == null) throw new RuntimeException("User not found"); // Quăng lỗi nếu không tìm thấy
        
        // 3. Lấy thông tin Xe, Chính sách giá và Chi nhánh từ DB dựa vào các ID mà Frontend gửi lên.
        // Nếu không tồn tại bất kỳ thông tin nào, sẽ văng lỗi (Exception).
        Vehicle vehicle = vehicleRepo.findById(req.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));
        PricePolicy policy = policyRepo.findById(req.getPolicyId())
                .orElseThrow(() -> new RuntimeException("Policy not found"));
        ParkingBranch branch = branchRepo.findById(req.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        // 4. Khởi tạo một đối tượng "Yêu cầu đăng ký vé tháng" mới
        MonthlyTicketRequest mtr = new MonthlyTicketRequest();
        mtr.setUser(user);
        mtr.setVehicle(vehicle);
        mtr.setPricePolicy(policy);
        mtr.setParkingBranch(branch);
        mtr.setStatus(0); // Gắn trạng thái mặc định: 0 = Pending (Đang chờ nhân viên duyệt)
        mtr.setCreatedAt(LocalDateTime.now()); // Thời gian tạo yêu cầu là ngay lúc này
        
        // 5. Lưu xuống Database và trả kết quả vừa lưu về cho Client
        return ResponseEntity.ok(requestRepo.save(mtr));
    }

    /**
     * API: Lấy danh sách TẤT CẢ các yêu cầu đăng ký vé tháng.
     * Quyền truy cập: Chỉ dành cho nhân viên nội bộ (STAFF, MANAGER, ADMIN).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<MonthlyTicketRequest>> getAllRequests() {
        // Trả về toàn bộ danh sách yêu cầu vé tháng có trong hệ thống
        return ResponseEntity.ok(requestRepo.findAll());
    }

    /**
     * API: Lấy danh sách các yêu cầu đăng ký vé tháng CỦA CÁ NHÂN người đang đăng nhập.
     * Quyền truy cập: Bất kỳ ai đã đăng nhập.
     */
    @GetMapping("/my-requests")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<MonthlyTicketRequest>> getMyRequests() {
        // 1. Xác định người dùng đang đăng nhập
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepo.findByUserEmail(username);
        if (user == null) user = userRepo.findByUserPhone(username);
        if (user == null) throw new RuntimeException("User not found");
        
        // 2. Tìm và trả về các yêu cầu vé tháng ứng với ID của người dùng đó
        return ResponseEntity.ok(requestRepo.findByUserUserId(user.getUserId()));
    }

    /**
     * API: Cập nhật trạng thái của một yêu cầu đăng ký vé tháng (Ví dụ: Chuyển từ Chờ duyệt -> Đã duyệt).
     * Quyền truy cập: Chỉ nhân viên nội bộ (STAFF, MANAGER, ADMIN).
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        // 1. Tìm kiếm yêu cầu theo ID truyền trên URL
        MonthlyTicketRequest req = requestRepo.findById(id).orElseThrow();
        
        // 2. Cập nhật trạng thái mới được gửi lên (qua tham số ?status=...)
        req.setStatus(status);
        
        // 3. Lưu lại vào DB và trả về kết quả
        return ResponseEntity.ok(requestRepo.save(req));
    }
}
