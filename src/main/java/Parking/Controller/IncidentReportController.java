package Parking.Controller;

import Parking.Service.IncidentReportService;
import Parking.dto.request.*;
import Parking.dto.response.IncidentReportResponse;
import Parking.dto.response.IncidentImageResponse;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import Parking.Service.IncidentImageService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/**
 * Controller xử lý các API Báo cáo và Quản lý Sự Cố (Ví dụ: Mất xe, mất thẻ, tai nạn trong bãi đỗ).
 */
@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@CrossOrigin("*")
@Tag(name = "Incident Report Controller", description = "Quản lý báo cáo và xử lý sự cố nâng cao")
@SecurityRequirement(name = "api_key")
public class IncidentReportController {

    private final IncidentReportService incidentReportService;
    private final IncidentImageService incidentImageService;
    private final Parking.Service.LostMonthlyCardReplacementService lostMonthlyCardReplacementService;

    /**
     * Người dùng (hoặc nhân viên) tạo một báo cáo sự cố chung.
     */
    @PostMapping
    @Operation(summary = "Tạo báo cáo sự cố chung")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<IncidentReportResponse> createReport(
            @Valid @RequestBody CreateIncidentRequest request
    ) {
        return ResponseEntity.ok(incidentReportService.createReport(request));
    }

    /**
     * Tính năng đặc biệt: Khách báo mất thẻ.
     * Thường logic bên trong Service sẽ tiến hành khóa thẻ ngay lập tức để tránh kẻ gian lấy xe ra.
     */
    @PostMapping("/lost-card")
    @Operation(summary = "Nghiệp vụ đặc thù: Báo mất thẻ giữ xe (Tự động khóa thẻ)")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<IncidentReportResponse> reportLostCard(
            @Valid @RequestBody LostCardIncidentRequest request
    ) {
        return ResponseEntity.ok(incidentReportService.reportLostCard(request));
    }

    /**
     * Quản lý phân công sự cố cho một nhân viên cụ thể đi xử lý.
     */
    @PutMapping("/{id}/assign")
    @Operation(summary = "Phân công nhân viên xử lý sự cố")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<IncidentReportResponse> assignIncident(
            @PathVariable Long id,
            @Valid @RequestBody AssignIncidentRequest request
    ) {
        return ResponseEntity.ok(incidentReportService.assignIncident(id, request));
    }

    /**
     * Đánh dấu sự cố đã được khắc phục/giải quyết xong.
     */
    @PutMapping("/{id}/resolve")
    @Operation(summary = "Cập nhật hoàn tất khắc phục sự cố")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<IncidentReportResponse> resolveIncident(
            @PathVariable Long id,
            @Valid @RequestBody ResolveIncidentRequest request
    ) {
        return ResponseEntity.ok(incidentReportService.resolveIncident(id, request));
    }

    /**
     * Hủy bỏ báo cáo sự cố (do báo nhầm, spam).
     */
    @PutMapping("/{id}/cancel")
    @Operation(summary = "Hủy báo cáo sự cố (do thông tin sai lệch/spam)")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<IncidentReportResponse> cancelIncident(
            @PathVariable Long id,
            @Valid @RequestBody CancelIncidentRequest request
    ) {
        return ResponseEntity.ok(incidentReportService.cancelIncident(id, request));
    }

    @PutMapping("/{id}/replace-monthly-card")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Cấp thẻ thay thế cho báo mất thẻ tháng")
    public ResponseEntity<IncidentReportResponse> replaceLostMonthlyCard(
            @PathVariable Long id,
            @Valid @RequestBody ReplaceLostMonthlyCardRequest request) {
        return ResponseEntity.ok(
                lostMonthlyCardReplacementService.replaceCard(
                        id, request.getReplacementCardId()));
    }

    /**
     * Lấy danh sách sự cố của chính người dùng đang đăng nhập (có phân trang).
     */
    @GetMapping("/my-incidents")
    @Operation(summary = "Lấy danh sách sự cố liên quan đến người đăng nhập (khách hàng)")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Page<IncidentReportResponse>> getMyIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // Tạo Pageable để phân trang, sắp xếp theo thời gian mới nhất
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(incidentReportService.getMyIncidents(pageable));
    }

    /**
     * Quản lý/Nhân viên lấy toàn bộ danh sách sự cố (Hỗ trợ lọc theo Chi nhánh, Trạng thái, Loại, Độ ưu tiên...).
     */
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
        // Sắp xếp ưu tiên: Độ nghiêm trọng (Priority) giảm dần -> Trạng thái -> Thời gian tạo mới nhất
        Sort sort = Sort.by(Sort.Order.desc("priority"), Sort.Order.asc("status"), Sort.Order.desc("createdAt"));
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(incidentReportService.getAllIncidents(
                branchId, status, type, priority, startDate, endDate, assignedStaffId, pageable));
    }

    /**
     * Xem chi tiết một báo cáo sự cố cụ thể bằng ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết sự cố theo ID")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<IncidentReportResponse> getReportById(@PathVariable Long id) {
        return ResponseEntity.ok(incidentReportService.getReportById(id));
    }

    @PostMapping(
        value = "/{id}/images",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "Upload ảnh bằng chứng cho báo cáo sự cố")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<IncidentImageResponse>> uploadIncidentImages(
            @PathVariable Long id,
            MultipartHttpServletRequest request
    ) {
        List<MultipartFile> files = new ArrayList<>();
        request.getMultiFileMap().values().forEach(files::addAll);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(incidentImageService.uploadImages(id, files));
    }

    @GetMapping("/{id}/images")
    @Operation(summary = "Lấy danh sách ảnh của sự cố")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<IncidentImageResponse>> getIncidentImages(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                incidentImageService.getImages(id)
        );
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @Operation(summary = "Xóa ảnh của sự cố")
    @PreAuthorize("hasAnyRole('USER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deleteIncidentImage(
            @PathVariable Long id,
            @PathVariable Long imageId
    ) {
        incidentImageService.deleteImage(id, imageId);
        return ResponseEntity.noContent().build();
    }
}
