package Parking.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import Parking.Service.ParkingSessionService;
import Parking.dto.request.GuestCheckInRequest;
import Parking.dto.request.GuestCheckOutRequest;
import Parking.dto.response.GuestCheckOutResponse;
import Parking.dto.response.ParkingSessionResponse;
import io.swagger.v3.oas.annotations.Operation;

import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
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

    @GetMapping()
    @Operation(summary = "Hàm lấy dữ liệu parkingSession")
    public ResponseEntity<List<ParkingSessionResponse>> getAllParkingSession(){
        List<ParkingSessionResponse> listParkingSession = parkingSessionService.getAllParkingSession();
        return ResponseEntity.ok(listParkingSession);
    }
    @PostMapping("/guest/check-out")
    @Operation(summary = "hàm check out  bãi xe")
    public ResponseEntity<GuestCheckOutResponse> guestCheckOut(
            @Valid @RequestBody GuestCheckOutRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);
        return ResponseEntity.ok(parkingSessionService.guestCheckOut(request, clientIp));
    }

    private String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
