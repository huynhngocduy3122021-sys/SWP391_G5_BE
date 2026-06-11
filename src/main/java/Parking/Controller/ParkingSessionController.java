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
import Parking.dto.response.ParkingSessionResponse;
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
    public ResponseEntity<ParkingSessionResponse> guestCheckIn(@Valid @RequestBody GuestCheckInRequest request) {
        return ResponseEntity.ok(parkingSessionService.guestCheckIn(request));
    }

    @GetMapping()
    public ResponseEntity<List<ParkingSessionResponse>> getAllParkingSession(){
        List<ParkingSessionResponse> listParkingSession = parkingSessionService.getAllParkingSession();
        return ResponseEntity.ok(listParkingSession);
    }
    @PostMapping("/guest/check-out")
    public ResponseEntity<ParkingSessionResponse> guestCheckOut(@Valid @RequestBody GuestCheckOutRequest request) {
        return ResponseEntity.ok(parkingSessionService.guestCheckOut(request));
    }
}
