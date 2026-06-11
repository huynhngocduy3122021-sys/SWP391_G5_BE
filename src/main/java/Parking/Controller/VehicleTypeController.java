package Parking.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Parking.Model.VehicleType;
import Parking.Service.VehicleTypeService;
import Parking.dto.request.CreateVehicleTypeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
@RestController
@RequestMapping("/api/vehicle-types")
@RequiredArgsConstructor
@CrossOrigin("*")
public class VehicleTypeController {
    private final VehicleTypeService vehicleTypeService;

    @PostMapping
    public ResponseEntity<VehicleType> createVehicleType(
            @Valid @RequestBody CreateVehicleTypeRequest request
    ) {
        return ResponseEntity.ok(vehicleTypeService.createVehicleType(request));
    }

    @GetMapping
    public ResponseEntity<List<VehicleType>> getAllVehicleTypes() {
        return ResponseEntity.ok(vehicleTypeService.getAllVehicleTypes());
    }
}
