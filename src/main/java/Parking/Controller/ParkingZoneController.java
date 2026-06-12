package Parking.Controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Parking.Model.ParkingZone;
import Parking.Service.ParkingZoneService;
import Parking.dto.request.CreateParkingZoneRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/parking-zones")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ParkingZoneController {

    private final ParkingZoneService parkingZoneService;

    @PostMapping
    @Operation(summary = "Hàm tạo khu vực giữ xe")
    public ResponseEntity<ParkingZone> createParkingZone(
            @Valid @RequestBody CreateParkingZoneRequest request
    ) {
        return ResponseEntity.ok(parkingZoneService.createParkingZone(request));
    }

    @GetMapping
    @Operation(summary = "Hàm lấy dữ liệu khu vực giữ xe")
    public ResponseEntity<List<ParkingZone>> getAllParkingZones() {
        return ResponseEntity.ok(parkingZoneService.getAllParkingZones());
    }
}
