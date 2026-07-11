package Parking.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import Parking.Service.ParkingSessionService;
import Parking.dto.request.GuestCheckInRequest;
import Parking.dto.request.GuestCheckOutRequest;
import Parking.dto.response.GuestCheckOutResponse;
import Parking.dto.response.ParkingSessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@SecurityRequirement(name = "api_key")
@RequestMapping("/api/parking-sessions")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ParkingSessionController {
    private final ParkingSessionService parkingSessionService;

    @PostMapping("/guest/check-in")
    @Operation(summary = "hàm check in bãi xe")
    public ResponseEntity<ParkingSessionResponse> guestCheckIn(@Valid @RequestBody GuestCheckInRequest request) {
        return ResponseEntity.ok(parkingSessionService.guestCheckIn(request));
    }

    @GetMapping("/active-session")
    @Operation(summary = "Lấy phiên gửi xe đang hoạt động bằng mã thẻ (để xem trước lúc Checkout)")
    public ResponseEntity<ParkingSessionResponse> getActiveSessionByCardCode(
            @RequestParam String cardCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time
    ) {
        return ResponseEntity.ok(parkingSessionService.getActiveSessionByCard(cardCode, time));
    }

    @GetMapping("/active-session/license-plate")
    @Operation(summary = "Lấy phiên gửi xe đang hoạt động bằng biển số xe (để phục vụ báo mất thẻ)")
    public ResponseEntity<ParkingSessionResponse> getActiveSessionByLicensePlate(
            @RequestParam String licensePlate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time
    ) {
        return ResponseEntity.ok(parkingSessionService.getActiveSessionByLicensePlate(licensePlate, time));
    }

    @GetMapping()
    @Operation(summary = "Hàm lấy dữ liệu parkingSession")
    public ResponseEntity<List<ParkingSessionResponse>> getAllParkingSession(){
        List<ParkingSessionResponse> listParkingSession = parkingSessionService.getAllParkingSession();
        return ResponseEntity.ok(listParkingSession);
    }
    @PostMapping("/booking/check-in")
    @Operation(summary = "Check-in cho xe đã đặt chỗ trước bằng mã booking và mã thẻ từ")
    public ResponseEntity<ParkingSessionResponse> bookingCheckIn(
            @RequestParam String bookingCode,
            @RequestParam String cardCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time
    ) {
        return ResponseEntity.ok(parkingSessionService.bookingCheckIn(bookingCode, cardCode, time));
    }

    @PostMapping("/guest/check-out")
    @Operation(summary = "hàm check out  bãi xe")
    public ResponseEntity<GuestCheckOutResponse> guestCheckOut(
            @Valid @RequestBody GuestCheckOutRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);
        return ResponseEntity.ok(parkingSessionService.guestCheckOut(request, clientIp));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
