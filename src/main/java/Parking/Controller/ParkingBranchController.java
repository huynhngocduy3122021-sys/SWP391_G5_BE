package Parking.Controller;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import Parking.Service.ParkingBranchService;
import Parking.dto.request.CreateParkingBranchRequest;
import Parking.dto.request.UpdateParkingBranchRequest;
import Parking.dto.response.ParkingBranchResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@SecurityRequirement(name = "api_key")
@RequestMapping("/api/parking-branches")
@CrossOrigin("*")
@RequiredArgsConstructor
public class ParkingBranchController {

    private final ParkingBranchService parkingBranchService;

    @PostMapping
    @Operation( summary = "Tạo chi nhánh bãi xe", description = " Tạo một chi nhánh bãi xe mới. Tên chi nhánh không được trùng với chi nhánh đã tồn tại. Chi nhánh mới sẽ có trạng thái hoạt động mặc định là active. ")
    public ResponseEntity<ParkingBranchResponse> create(@Valid @RequestBody CreateParkingBranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parkingBranchService.createParkingBranch(request));
    }

    @GetMapping
    @Operation( summary = "Lấy danh sách chi nhánh bãi xe", description = "Trả về toàn bộ các chi nhánh bãi xe đang có trong hệ thống." )
    public ResponseEntity<List<ParkingBranchResponse>> getAll() {
        return ResponseEntity.ok(parkingBranchService.getAllParkingBranches());
    }

    @GetMapping("/{id}")
    @Operation( summary = "Lấy chi tiết chi nhánh bãi xe", description = "Tìm và trả về thông tin chi tiết của một chi nhánh theo ID." )
    public ResponseEntity<ParkingBranchResponse> getById(@PathVariable Long id) {
         return ResponseEntity.ok(parkingBranchService.getParkingBranchById(id));
    }

    @PutMapping("/{id}")
    @Operation( summary = "Cập nhật thông tin chi nhánh bãi xe", description = " Cập nhật tên, địa chỉ, số điện thoại và mô tả của chi nhánh. ID của chi nhánh được truyền trên đường dẫn. " )
    public ResponseEntity<ParkingBranchResponse> update(@PathVariable Long id,@Valid @RequestBody UpdateParkingBranchRequest request) {
        return ResponseEntity.ok(parkingBranchService.updateParkingBranch(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation( summary = "Thay đổi trạng thái chi nhánh bãi xe", description = " Bật hoặc tắt trạng thái hoạt động của chi nhánh. active = true: Chi nhánh đang hoạt động. active = false: Chi nhánh ngừng hoạt động. " )
    public ResponseEntity<ParkingBranchResponse> updateStatus(@PathVariable Long id,@RequestParam boolean active) {
        return ResponseEntity.ok(parkingBranchService.updateStatus(id, active));
    }
}