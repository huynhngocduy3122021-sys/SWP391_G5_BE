package Parking.Controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Parking.Service.BookingService;
import Parking.dto.request.CreateBookingRequest;
import Parking.dto.response.BookingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller xử lý các API liên quan đến chức năng "Đặt chỗ đậu xe trước" (Booking).
 */
@RestController // Khai báo đây là một REST Controller
@RequestMapping("/api/bookings") // Đường dẫn gốc cho các API đặt chỗ
@RequiredArgsConstructor // Tự động tạo Constructor cho các hằng số final (bookingService)
@CrossOrigin("*") // Cho phép Frontend từ domain khác gọi vào không bị lỗi CORS
@SecurityRequirement(name = "api_key") // Cấu hình Swagger yêu cầu xác thực
public class BookingController {

    // Kéo BookingService vào để xử lý các logic nghiệp vụ đặt chỗ
    private final BookingService bookingService;

    /**
     * API: Tạo yêu cầu đặt chỗ trước.
     * Phương thức: POST
     */
    @PostMapping
    @Operation(summary = "Tạo yêu cầu đặt chỗ đỗ xe trước (Booking)")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        // Gọi service tạo booking và trả về mã 201 (CREATED) nếu thành công
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request));
    }

    /**
     * API: Lấy danh sách lịch sử đặt chỗ của khách hàng ĐANG ĐĂNG NHẬP.
     * Phương thức: GET
     */
    @GetMapping("/my-bookings")
    @Operation(summary = "Lấy danh sách các yêu cầu đặt chỗ của người dùng hiện tại")
    public ResponseEntity<List<BookingResponse>> getMyBookings() {
        return ResponseEntity.ok(bookingService.getMyBookings());
    }

    /**
     * API: Lấy tất cả danh sách đặt chỗ trong hệ thống.
     * Dành cho quản trị viên hoặc nhân viên bãi đỗ.
     */
    @GetMapping
    @Operation(summary = "Lấy toàn bộ danh sách đặt chỗ (Dành cho Quản trị viên/Nhân viên)")
    public ResponseEntity<List<BookingResponse>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    /**
     * API: Hủy một yêu cầu đặt chỗ (dựa vào ID).
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy yêu cầu đặt chỗ")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    /**
     * API: Tìm kiếm thông tin đặt chỗ bằng mã booking code.
     * Ứng dụng: Nhân viên quét mã QR của khách hoặc nhập tay mã để kiểm tra.
     */
    @GetMapping("/code/{bookingCode}")
    @Operation(summary = "Tìm kiếm đặt chỗ bằng mã booking (dành cho Nhân viên quét QR hoặc nhập tay)")
    public ResponseEntity<BookingResponse> getBookingByCode(@PathVariable String bookingCode) {
        return ResponseEntity.ok(bookingService.getBookingByCode(bookingCode));
    }
}
