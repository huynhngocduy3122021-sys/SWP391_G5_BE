package Parking.Controller;

import Parking.Service.IncidentReportService;
import Parking.dto.request.*;
import Parking.dto.response.IncidentReportResponse;
import Parking.enums.IncidentPriority;
import Parking.enums.IncidentStatus;
import Parking.enums.IncidentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@CrossOrigin("*")
@Tag(name = "Incident Report Controller", description = "Quản lý báo cáo và xử lý sự cố nâng cao")
@SecurityRequirement(name = "api_key")
public class IncidentReportController {

    private final IncidentReportService incidentReportService;

    @PostMapping
    @Operation(summary = "Tạo báo cáo sự cố chung")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<IncidentReportResponse> createReport(
            @Valid @RequestBody CreateIncidentRequest request
    ) {
        return ResponseEntity.ok(incidentReportService.createReport(request));
    }

    @PostMapping("/lost-card")
    @Operation(summary = "Nghiệp vụ đặc thù: Báo mất thẻ giữ xe (Tự động khóa thẻ)")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<IncidentReportResponse> reportLostCard(
            @Valid @RequestBody LostCardIncidentRequest request
    ) {
        return ResponseEntity.ok(incidentReportService.reportLostCard(request));
    }

    @PutMapping("/{id}/assign")
    @Operation(summary = "Phân công nhân viên xử lý sự cố")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<IncidentReportResponse> assignIncident(
            @PathVariable Long id,
            @Valid @RequestBody AssignIncidentRequest request
    ) {
        return ResponseEntity.ok(incidentReportService.assignIncident(id, request));
    }

    @PutMapping("/{id}/resolve")
    @Operation(summary = "Cập nhật hoàn tất khắc phục sự cố")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<IncidentReportResponse> resolveIncident(
            @PathVariable Long id,
            @Valid @RequestBody ResolveIncidentRequest request
    ) {
        return ResponseEntity.ok(incidentReportService.resolveIncident(id, request));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Hủy báo cáo sự cố (do thông tin sai lệch/spam)")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<IncidentReportResponse> cancelIncident(
            @PathVariable Long id,
            @Valid @RequestBody CancelIncidentRequest request
    ) {
        return ResponseEntity.ok(incidentReportService.cancelIncident(id, request));
    }

    @GetMapping("/my-incidents")
    @Operation(summary = "Lấy danh sách sự cố liên quan đến người đăng nhập (khách hàng)")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Page<IncidentReportResponse>> getMyIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(incidentReportService.getMyIncidents(pageable));
    }

    @GetMapping
    @Operation(summary = "Xem danh sách toàn bộ sự cố có bộ lọc và phân trang (Admin/Manager/Staff)")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Page<IncidentReportResponse>> getAllIncidents(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentType type,
            @RequestParam(required = false) IncidentPriority priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) Long assignedStaffId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // Thứ tự sắp xếp ưu tiên: CRITICAL -> PENDING -> thời gian tạo mới nhất
        // Trong Spring Data JPA, ta có thể sắp xếp theo nhiều tiêu chí
        Sort sort = Sort.by(Sort.Order.desc("priority"), Sort.Order.asc("status"), Sort.Order.desc("createdAt"));
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(incidentReportService.getAllIncidents(
                branchId, status, type, priority, startDate, endDate, assignedStaffId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết sự cố theo ID")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<IncidentReportResponse> getReportById(@PathVariable Long id) {
        return ResponseEntity.ok(incidentReportService.getReportById(id));
    }
}
