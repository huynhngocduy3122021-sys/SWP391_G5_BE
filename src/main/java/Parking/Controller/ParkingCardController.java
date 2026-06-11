package Parking.Controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Parking.Model.ParkingCard;
import Parking.Service.ParkingCardService;
import Parking.dto.request.CreateParkingCardRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/parking-cards")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ParkingCardController {
    private final ParkingCardService parkingCardService;

    @PostMapping
    @Operation(summary = "Tạo thẻ giữ xe")
    public ResponseEntity<ParkingCard> createParkingCard(
            @Valid @RequestBody CreateParkingCardRequest request
    ) {
        return ResponseEntity.ok(parkingCardService.createParkingCard(request));
    }

    @GetMapping
    @Operation(summary = "Lấy dữ liệu của thẻ")
    public ResponseEntity<List<ParkingCard>> getAllParkingCards() {
        return ResponseEntity.ok(parkingCardService.getAllParkingCards());
    }
}
