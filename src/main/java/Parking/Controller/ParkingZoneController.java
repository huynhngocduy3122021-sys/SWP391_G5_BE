
package Parking.Controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import Parking.Service.ParkingZoneService;
import Parking.dto.request.CreateParkingZoneRequest;
import Parking.dto.request.UpdateParkingZoneRequest;
import Parking.dto.response.ParkingZoneResponse;
import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/parking-zones")
@CrossOrigin("*")
@RequiredArgsConstructor
public class ParkingZoneController {

    private final ParkingZoneService parkingZoneService;

    @PostMapping
    @Operation(summary = "Tạo khu vực đỗ xe",description = "Tạo một khu vực đỗ xe mới thuộc một tầng cụ thể.Mỗi tầng chỉ được liên kết với một khu vực đỗ xe. Khu vực phải được gán loại phương tiện và sức chứa tối đa.")
        
    public ResponseEntity<ParkingZoneResponse> create(@Valid @RequestBody CreateParkingZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parkingZoneService.createParkingZone(request));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách khu vực đỗ xe",description = "Trả về toàn bộ các khu vực đỗ xehiện có trong hệ thống." )
    
    public ResponseEntity<List<ParkingZoneResponse>> getAll() {
        return ResponseEntity.ok(
            parkingZoneService.getAllParkingZones()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết khu vực đỗ xe",description = "Tìm và trả về thông tin chi tiết của một khu vực đỗ xe theo ID.")
    
    public ResponseEntity<ParkingZoneResponse> getById( @PathVariable Long id) {
        return ResponseEntity.ok(parkingZoneService.getParkingZoneById(id));
    }

    @GetMapping("/branch/{branchId}")
    @Operation(summary = "Lấy danh sách khu vực đỗ xe theo chi nhánh",description = " Trả về tất cả các khu vực đỗ xe thuộc các tầng của một chi nhánh bãi xe.")
    
    public ResponseEntity<List<ParkingZoneResponse>> getByBranch( @PathVariable Long branchId) {
        return ResponseEntity.ok(parkingZoneService.getZonesByBranch(branchId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin khu vực đỗ xe",description = "Cập nhật tên khu vực, sức chứa, tầng bãi xe và loại phương tiện của khu vực đỗ xe.Tầng mới không được liên kết với một khu vực đỗ xe khác.")
    
    public ResponseEntity<ParkingZoneResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateParkingZoneRequest request) {
        return ResponseEntity.ok(
            parkingZoneService.updateParkingZone(id, request)
        );
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Thay đổi trạng thái khu vực đỗ xe",description = "Bật hoặc tắt trạng thái hoạt động của khu vực đỗ xe. active = true: Khu vực đang được sử dụng.active = false: Khu vực ngừng hoạt động và không được tính vào sức chứa bãi xe. ")
    public ResponseEntity<ParkingZoneResponse> updateStatus(@PathVariable Long id,@RequestParam boolean active) {
        return ResponseEntity.ok(parkingZoneService.updateStatus(id, active));}
    }
