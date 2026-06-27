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

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@CrossOrigin("*")
@SecurityRequirement(name = "api_key")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Tạo yêu cầu đặt chỗ đỗ xe trước (Booking)")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request));
    }

    @GetMapping("/my-bookings")
    @Operation(summary = "Lấy danh sách các yêu cầu đặt chỗ của người dùng hiện tại")
    public ResponseEntity<List<BookingResponse>> getMyBookings() {
        return ResponseEntity.ok(bookingService.getMyBookings());
    }

    @GetMapping
    @Operation(summary = "Lấy toàn bộ danh sách đặt chỗ (Dành cho Quản trị viên/Nhân viên)")
    public ResponseEntity<List<BookingResponse>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy yêu cầu đặt chỗ")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    @GetMapping("/code/{bookingCode}")
    @Operation(summary = "Tìm kiếm đặt chỗ bằng mã booking (dành cho Nhân viên quét QR hoặc nhập tay)")
    public ResponseEntity<BookingResponse> getBookingByCode(@PathVariable String bookingCode) {
        return ResponseEntity.ok(bookingService.getBookingByCode(bookingCode));
    }
}
