package Parking.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Parking.Model.VehicleType;
import Parking.Service.VehicleTypeService;
import Parking.dto.request.CreateVehicleTypeRequest;
import Parking.dto.request.UpdateVehicleTypeRequest;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "hàm tạo ra những loại xe")
    public ResponseEntity<VehicleType> createVehicleType(
            @Valid @RequestBody CreateVehicleTypeRequest request
    ) {
        return ResponseEntity.ok(vehicleTypeService.createVehicleType(request));
    }

    @GetMapping
    @Operation(summary = "Hàm lấy những thông tin loại xe")
    public ResponseEntity<List<VehicleType>> getAllVehicleTypes() {
        return ResponseEntity.ok(vehicleTypeService.getAllVehicleTypes());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Hàm lấy chi tiết loại xe theo ID")
    public ResponseEntity<VehicleType> getVehicleTypeById(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleTypeService.getVehicleTypeById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Hàm cập nhật loại xe theo ID")
    public ResponseEntity<VehicleType> updateVehicleType(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVehicleTypeRequest request
    ) {
        return ResponseEntity.ok(vehicleTypeService.updateVehicleType(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Hàm xóa loại xe theo ID")
    public ResponseEntity<Void> deleteVehicleType(@PathVariable Long id) {
        vehicleTypeService.deleteVehicleType(id);
        return ResponseEntity.noContent().build();
    }
}
