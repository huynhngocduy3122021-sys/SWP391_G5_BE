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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/parking-cards")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ParkingCardController {
    private final ParkingCardService parkingCardService;

    @PostMapping
    public ResponseEntity<ParkingCard> createParkingCard(
            @Valid @RequestBody CreateParkingCardRequest request
    ) {
        return ResponseEntity.ok(parkingCardService.createParkingCard(request));
    }

    @GetMapping
    public ResponseEntity<List<ParkingCard>> getAllParkingCards() {
        return ResponseEntity.ok(parkingCardService.getAllParkingCards());
    }
}
