package Parking.Controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import Parking.Service.ParkingCardService;
import Parking.dto.request.CreateParkingCardRequest;
import Parking.dto.request.UpdateParkingCardRequest;
import Parking.dto.response.ParkingCardResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@SecurityRequirement(name = "api_key")
@RequestMapping("/api/parking-cards")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ParkingCardController {
    private final ParkingCardService parkingCardService;

    @PostMapping
    @Operation(summary = "Tạo thẻ giữ xe")
    public ResponseEntity<ParkingCardResponse> createParkingCard(
            @Valid @RequestBody CreateParkingCardRequest request
    ) {
        return ResponseEntity.ok(parkingCardService.createParkingCard(request));
    }

    @GetMapping
    @Operation(summary = "Lấy dữ liệu của thẻ")
    public ResponseEntity<List<ParkingCardResponse>> getAllParkingCards() {
        return ResponseEntity.ok(parkingCardService.getAllParkingCards());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thẻ giữ xe theo ID")
    public ResponseEntity<ParkingCardResponse> getParkingCardById(@PathVariable Long id) {
        return ResponseEntity.ok(parkingCardService.getParkingCardById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin thẻ giữ xe")
    public ResponseEntity<ParkingCardResponse> updateParkingCard(
            @PathVariable Long id,
            @Valid @RequestBody UpdateParkingCardRequest request
    ) {
        return ResponseEntity.ok(parkingCardService.updateParkingCard(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa thẻ giữ xe")
    public ResponseEntity<Void> deleteParkingCard(@PathVariable Long id) {
        parkingCardService.deleteParkingCard(id);
        return ResponseEntity.noContent().build();
    }
}
