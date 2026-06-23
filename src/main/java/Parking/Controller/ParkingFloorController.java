
package Parking.Controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import Parking.Service.ParkingFloorService;
import Parking.dto.request.CreateParkingFloorRequest;
import Parking.dto.request.UpdateParkingFloorRequest;
import Parking.dto.response.ParkingFloorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/parking-floors")
@CrossOrigin("*")
@RequiredArgsConstructor
public class ParkingFloorController {

    private final ParkingFloorService parkingFloorService;

    @PostMapping
    @Operation(summary = "Tạo tầng bãi xe",description = "Tạo một tầng bãi xe mới thuộc một chi nhánh.Mỗi tầng phải thuộc một chi nhánh bãi xe đang tồn tại và số tầng không được trùng trong cùng một chi nhánh.")
    
    public ResponseEntity<ParkingFloorResponse> create(@Valid @RequestBody CreateParkingFloorRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(parkingFloorService.createParkingFloor(request));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách tầng bãi xe", description = "Trả về toàn bộ các tầng bãi xehiện có trong hệ thống.")
    
    public ResponseEntity<List<ParkingFloorResponse>> getAll() {
        return ResponseEntity.ok(parkingFloorService.getAllParkingFloors());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết tầng bãi xe",description = "Tìm và trả về thông tin chi tiếtcủa một tầng bãi xe theo ID.")
    
    public ResponseEntity<ParkingFloorResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok( parkingFloorService.getParkingFloorById(id));
    }

    @GetMapping("/branch/{branchId}")
    @Operation(summary = "Lấy danh sách tầng theo chi nhánh",description = "Trả về danh sách các tầng thuộc một chi nhánh bãi xe.Danh sách tầng được sắp xếp theo số tầng tăng dần.")
    public ResponseEntity<List<ParkingFloorResponse>> getByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(
            parkingFloorService.getFloorsByBranch(branchId)
        );
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin tầng bãi xe",description = "Cập nhật tên tầng, số tầng, mô tảvà chi nhánh của tầng bãi xe.Số tầng không được trùng với tầng khác trong cùng một chi nhánh.")
    
    public ResponseEntity<ParkingFloorResponse> update(
        @PathVariable Long id,@Valid @RequestBody UpdateParkingFloorRequest request) {
        return ResponseEntity.ok(parkingFloorService.updateParkingFloor(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Thay đổi trạng thái tầng bãi xe",description = "Bật hoặc tắt trạng thái hoạt động của tầng bãi xe.active = true: Tầng bãi xe đang hoạt động.active = false: Tầng bãi xe ngừng hoạt động.")
    public ResponseEntity<ParkingFloorResponse> updateStatus(@Parameter @PathVariable Long id,@RequestParam boolean active) {
        return ResponseEntity.ok(parkingFloorService.updateStatus(id, active));
    
    }
}

