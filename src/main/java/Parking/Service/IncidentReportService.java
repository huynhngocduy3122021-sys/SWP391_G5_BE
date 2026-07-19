package Parking.Service;

import Parking.Model.*;
import Parking.Repository.*;
import Parking.dto.request.*;
import Parking.dto.response.IncidentReportResponse;
import Parking.enums.*;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentReportService {

    private final IncidentReportRepository incidentReportRepository;
    private final UserRepository userRepository;
    private final ParkingBranchRepository parkingBranchRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ParkingCardRepository parkingCardRepository;
    private final IncidentImageRepository incidentImageRepository;
    private final IncidentLogRepository incidentLogRepository;
    private final BranchScopeService branchScopeService;

    // Lấy User hiện tại đăng nhập từ Security Context
    public User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
        }
        throw new ParkingSessionException("Yêu cầu xác thực tài khoản!");
    }

    @Transactional
    public IncidentReportResponse createReport(CreateIncidentRequest request) {
        User reporter = getCurrentUser();
        ParkingBranch branch = null;
        ParkingSession session = null;
        ParkingCard card = null;

        // 1. Kiểm tra logic ParkingSession và ParkingBranch tương ứng
        if (request.getParkingSessionId() != null) {
            session = parkingSessionRepository.findById(request.getParkingSessionId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy phiên giữ xe liên quan"));

            branch = session.getParkingBranch(); // Chống không nhất quán chi nhánh

            if (request.getParkingBranchId() != null && !request.getParkingBranchId().equals(branch.getParkingBranchId())) {
                throw new ParkingSessionException("Chi nhánh gửi xe không khớp với phiên gửi xe tương ứng");
            }
        } else {
            if (request.getParkingBranchId() == null) {
                throw new ParkingSessionException("Yêu cầu cung cấp chi nhánh xảy ra sự cố!");
            }
            branch = parkingBranchRepository.findById(request.getParkingBranchId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy chi nhánh bãi xe"));
        }

        if (!branch.isActive()) {
            throw new ParkingSessionException("Chi nhánh bãi xe này hiện tại đang ngừng hoạt động!");
        }

        // Validate lỗi kỹ thuật phải có mô tả vị trí cụ thể
        if ((request.getIncidentType() == IncidentType.TECHNICAL_ERROR || request.getIncidentType() == IncidentType.BARRIER_ERROR)
                && (request.getLocationDetails() == null || request.getLocationDetails().isBlank())) {
            throw new ParkingSessionException("Sự cố kỹ thuật hoặc lỗi barrier cần mô tả chi tiết vị trí xảy ra sự cố!");
        }

        // 2. Xử lý nghiệp vụ LOST_CARD
        if (request.getIncidentType() == IncidentType.LOST_CARD) {
            if (session != null) {
                card = session.getParkingCard();
            } else if (request.getParkingCardId() != null) {
                card = parkingCardRepository.findById(request.getParkingCardId())
                        .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thẻ giữ xe"));
            }

            if (card == null) {
                throw new ParkingSessionException("Cần cung cấp thông tin thẻ xe hoặc phiên giữ xe để báo mất thẻ!");
            }

            // Chống tạo trùng lặp ticket mất thẻ chưa được đóng
            boolean hasDuplicate = incidentReportRepository.existsByParkingCardParkingCardIdAndIncidentTypeAndStatusIn(
                    card.getParkingCardId(),
                    IncidentType.LOST_CARD,
                    List.of(IncidentStatus.PENDING, IncidentStatus.IN_PROGRESS, IncidentStatus.WAITING_PAYMENT, IncidentStatus.WAITING_MANAGER_APPROVAL)
            );
            if (hasDuplicate) {
                throw new ParkingSessionException("Đã tồn tại một báo cáo mất thẻ đang được xử lý cho thẻ này!");
            }

            // Vô hiệu hóa thẻ lập tức
            card.setStatus(ParkingCardStatus.LOST);
            parkingCardRepository.save(card);
        }

        // 3. Khởi tạo đối tượng IncidentReport
        IncidentReport report = new IncidentReport();
        report.setTitle(request.getTitle());
        report.setDescription(request.getDescription());
        report.setIncidentType(request.getIncidentType());
        report.setPriority(request.getPriority());
        report.setStatus(IncidentStatus.PENDING);
        report.setReporter(reporter);
        report.setParkingBranch(branch);
        report.setParkingSession(session);
        report.setParkingCard(card);
        report.setLocationDetails(request.getLocationDetails());
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        // 4. Lưu ảnh hiện trường đính kèm
        if (request.getImages() != null) {
            for (CreateIncidentRequest.ImageDto imgDto : request.getImages()) {
                IncidentImage img = new IncidentImage();
                img.setImageUrl(imgDto.getImageUrl());
                img.setPublicId(imgDto.getPublicId());
                img.setUploadedBy(reporter);
                img.setUploadedAt(LocalDateTime.now());
                report.addImage(img);
            }
        }

        // 5. Lưu vết nhật ký khởi tạo (Audit Log)
        IncidentLog initialLog = new IncidentLog();
        initialLog.setChangedBy(reporter);
        initialLog.setChangedAt(LocalDateTime.now());
        initialLog.setOldStatus(null);
        initialLog.setNewStatus(IncidentStatus.PENDING);
        initialLog.setActionType(IncidentLogAction.CREATE);
        initialLog.setDescription("Sự cố đã được khởi tạo bởi " + reporter.getUserFullName());
        report.addLog(initialLog);

        return convertToResponse(incidentReportRepository.save(report));
    }

    @Transactional
    public IncidentReportResponse reportLostCard(LostCardIncidentRequest request) {
        User reporter = getCurrentUser();

        ParkingSession session = parkingSessionRepository.findById(request.getParkingSessionId())
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy phiên giữ xe đang hoạt động"));

        if (session.getStatus() != ParkingSessionStatus.ACTIVE) {
            throw new ParkingSessionException("Phiên giữ xe này đã kết thúc, không thể báo mất thẻ!");
        }

        ParkingCard card = session.getParkingCard();
        if (card == null) {
            throw new ParkingSessionException("Không tìm thấy thẻ giữ xe đi kèm với phiên này!");
        }

        // Xác thực cardCode nếu được truyền vào
        if (request.getCardCode() != null && !request.getCardCode().isBlank()
                && !card.getCardCode().equalsIgnoreCase(request.getCardCode().trim())) {
            throw new ParkingSessionException("Mã thẻ xe không trùng khớp với phiên gửi xe hiện tại!");
        }

        // Chống tạo trùng ticket mất thẻ chưa xử lý xong
        boolean hasDuplicate = incidentReportRepository.existsByParkingCardParkingCardIdAndIncidentTypeAndStatusIn(
                card.getParkingCardId(),
                IncidentType.LOST_CARD,
                List.of(IncidentStatus.PENDING, IncidentStatus.IN_PROGRESS, IncidentStatus.WAITING_PAYMENT, IncidentStatus.WAITING_MANAGER_APPROVAL)
        );
        if (hasDuplicate) {
            throw new ParkingSessionException("Thẻ xe này đã được báo mất và đang trong quá trình xử lý!");
        }

        // Khóa thẻ lập tức
        card.setStatus(ParkingCardStatus.LOST);
        parkingCardRepository.save(card);

        // Tạo Incident
        IncidentReport report = new IncidentReport();
        report.setTitle("Khách hàng báo mất thẻ: " + card.getCardCode());
        report.setDescription(request.getDescription());
        report.setIncidentType(IncidentType.LOST_CARD);
        report.setPriority(IncidentPriority.HIGH);
        report.setStatus(IncidentStatus.PENDING);
        report.setReporter(reporter);
        report.setParkingBranch(session.getParkingBranch());
        report.setParkingSession(session);
        report.setParkingCard(card);
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        // Ghi Log Audit
        IncidentLog log = new IncidentLog();
        log.setChangedBy(reporter);
        log.setChangedAt(LocalDateTime.now());
        log.setOldStatus(null);
        log.setNewStatus(IncidentStatus.PENDING);
        log.setActionType(IncidentLogAction.CREATE);
        log.setDescription("Khách hàng báo mất thẻ qua hệ thống. Thẻ " + card.getCardCode() + " đã tự động bị khóa sang LOST.");
        report.addLog(log);

        return convertToResponse(incidentReportRepository.save(report));
    }

    @Transactional
    public IncidentReportResponse assignIncident(Long id, AssignIncidentRequest request) {
        User operator = getCurrentUser();
        IncidentReport report = incidentReportRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy báo cáo sự cố"));

        if (report.getStatus() == IncidentStatus.RESOLVED || report.getStatus() == IncidentStatus.CANCELLED) {
            throw new ParkingSessionException("Không thể phân công công việc cho sự cố đã đóng hoặc đã hủy");
        }

        // Kiểm tra quyền chi nhánh: Admin được thao tác toàn bộ, Manager/Staff chỉ thao tác nhánh của mình
        branchScopeService.assertSameBranch(report.getParkingBranch().getParkingBranchId());

        User staff = userRepository.findById(request.getAssignedStaffId())
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy nhân viên được phân công"));

        if (staff.getUserRole() != UserRole.STAFF && staff.getUserRole() != UserRole.MANAGER) {
            throw new ParkingSessionException("Người nhận phân công phải là Nhân viên hoặc Quản lý");
        }

        IncidentStatus oldStatus = report.getStatus();
        report.setAssignedStaff(staff);
        report.setStatus(IncidentStatus.IN_PROGRESS);
        if (request.getPriority() != null) {
            report.setPriority(request.getPriority());
        }
        report.setUpdatedAt(LocalDateTime.now());

        // Ghi log
        IncidentLog log = new IncidentLog();
        log.setChangedBy(operator);
        log.setChangedAt(LocalDateTime.now());
        log.setOldStatus(oldStatus);
        log.setNewStatus(IncidentStatus.IN_PROGRESS);
        log.setActionType(IncidentLogAction.ASSIGN);
        log.setDescription("Phân công sự cố cho nhân viên: " + staff.getUserFullName() + " (Độ ưu tiên: " + report.getPriority() + ")");
        report.addLog(log);

        return convertToResponse(incidentReportRepository.save(report));
    }

    @Transactional
    public IncidentReportResponse resolveIncident(Long id, ResolveIncidentRequest request) {
        User staff = getCurrentUser();
        IncidentReport report = incidentReportRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy báo cáo sự cố"));

        // Bảo mật phân quyền xử lý
        if (staff.getUserRole() == UserRole.STAFF &&
            (report.getAssignedStaff() == null || !report.getAssignedStaff().getUserId().equals(staff.getUserId()))) {
            throw new ParkingSessionException("Bạn không phải nhân viên được giao trách nhiệm giải quyết sự cố này!");
        }

        // Kiểm tra quyền chi nhánh: Admin được thao tác toàn bộ, Manager/Staff chỉ thao tác nhánh của mình
        branchScopeService.assertSameBranch(report.getParkingBranch().getParkingBranchId());

        if (report.getStatus() == IncidentStatus.RESOLVED) {
            throw new ParkingSessionException("Sự cố đã được đánh dấu hoàn thành từ trước");
        }

        // Nghiệp vụ mất thẻ: kiểm tra điều kiện hoàn thành (RESOLVED)
        if (report.getIncidentType() == IncidentType.LOST_CARD) {
            ParkingSession session = report.getParkingSession();
            if (session != null && session.getStatus() == ParkingSessionStatus.ACTIVE) {
                // Yêu cầu check-out xe trước khi resolve ticket báo mất thẻ
                throw new ParkingSessionException("Không thể hoàn tất sự cố mất thẻ khi phiên gửi xe của phương tiện vẫn đang hoạt động. Vui lòng thực hiện Check-out xe trước.");
            }
        }

        IncidentStatus oldStatus = report.getStatus();
        report.setStatus(IncidentStatus.RESOLVED);
        report.setResolutionNotes(request.getResolutionNotes());
        if (request.getLostCardFee() != null) {
            report.setLostCardFee(request.getLostCardFee());
        }
        report.setResolvedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        // Ghi log
        IncidentLog log = new IncidentLog();
        log.setChangedBy(staff);
        log.setChangedAt(LocalDateTime.now());
        log.setOldStatus(oldStatus);
        log.setNewStatus(IncidentStatus.RESOLVED);
        log.setActionType(IncidentLogAction.RESOLVE);
        log.setDescription("Sự cố được giải quyết xong bởi " + staff.getUserFullName() + ". Ghi chú: " + request.getResolutionNotes());
        report.addLog(log);

        return convertToResponse(incidentReportRepository.save(report));
    }

    @Transactional
    public IncidentReportResponse cancelIncident(Long id, CancelIncidentRequest request) {
        User operator = getCurrentUser();
        IncidentReport report = incidentReportRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy báo cáo sự cố"));

        if (report.getStatus() == IncidentStatus.RESOLVED) {
            throw new ParkingSessionException("Không thể hủy sự cố đã được khắc phục hoàn tất!");
        }

        // Kiểm tra quyền chi nhánh: Admin được thao tác toàn bộ, Manager/Staff chỉ thao tác nhánh của mình
        branchScopeService.assertSameBranch(report.getParkingBranch().getParkingBranchId());

        IncidentStatus oldStatus = report.getStatus();
        report.setStatus(IncidentStatus.CANCELLED);
        report.setCancellationReason(request.getCancellationReason());
        report.setCancelledAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        // Rollback trạng thái của thẻ xe khi hủy báo mất thẻ (nếu có)
        if (report.getIncidentType() == IncidentType.LOST_CARD && report.getParkingCard() != null) {
            ParkingCard card = report.getParkingCard();
            ParkingSession session = report.getParkingSession();
            if (session != null && session.getStatus() == ParkingSessionStatus.ACTIVE) {
                // Rollback thẻ về trạng thái đang sử dụng nếu session còn kích hoạt
                card.setStatus(ParkingCardStatus.IN_USE);
                parkingCardRepository.save(card);
            }
        }

        // Ghi log
        IncidentLog log = new IncidentLog();
        log.setChangedBy(operator);
        log.setChangedAt(LocalDateTime.now());
        log.setOldStatus(oldStatus);
        log.setNewStatus(IncidentStatus.CANCELLED);
        log.setActionType(IncidentLogAction.CANCEL);
        log.setDescription("Hủy sự cố do: " + request.getCancellationReason());
        report.addLog(log);

        return convertToResponse(incidentReportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public Page<IncidentReportResponse> getMyIncidents(Pageable pageable) {
        User user = getCurrentUser();
        return incidentReportRepository.findByReporterUserId(user.getUserId(), pageable)
                .map(this::convertToResponse);
    }

    @Transactional(readOnly = true)
    public Page<IncidentReportResponse> getAllIncidents(
            Long branchId,
            IncidentStatus status,
            IncidentType type,
            IncidentPriority priority,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long assignedStaffId,
            Pageable pageable
    ) {
        Long scopedBranchId = branchScopeService.resolveReadableBranchId(branchId);
        
        return incidentReportRepository.findByFilters(
                scopedBranchId, status, type, priority, startDate, endDate, assignedStaffId, pageable)
                .map(this::convertToResponse);
    }

    @Transactional(readOnly = true)
    public IncidentReportResponse getReportById(Long id) {
        User user = getCurrentUser();
        IncidentReport report = incidentReportRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy báo cáo sự cố"));

        // Phân quyền chi tiết
        if (user.getUserRole() == UserRole.USER) {
            if (!report.getReporter().getUserId().equals(user.getUserId())) {
                throw new ParkingSessionException("Bạn không có quyền xem thông tin sự cố của người khác!");
            }
        } else {
            // Kiểm tra quyền chi nhánh: Admin xem toàn bộ, Manager/Staff chỉ xem nhánh của mình
            branchScopeService.assertSameBranch(report.getParkingBranch().getParkingBranchId());
        }

        return convertToResponse(report);
    }

    private IncidentReportResponse convertToResponse(IncidentReport report) {
        User user = getCurrentUser();

        List<IncidentReportResponse.IncidentImageResponse> imgs = report.getIncidentImages().stream()
                .map(i -> IncidentReportResponse.IncidentImageResponse.builder()
                        .incidentImageId(i.getIncidentImageId())
                        .imageUrl(i.getImageUrl())
                        .uploadedAt(i.getUploadedAt())
                        .build())
                .toList();

        List<IncidentReportResponse.IncidentLogResponse> logs = report.getIncidentLogs().stream()
                .map(l -> IncidentReportResponse.IncidentLogResponse.builder()
                        .logId(l.getLogId())
                        .changedByName(l.getChangedBy().getUserFullName())
                        .changedAt(l.getChangedAt())
                        .oldStatus(l.getOldStatus())
                        .newStatus(l.getNewStatus())
                        .actionType(l.getActionType().name())
                        .description(l.getDescription())
                        .build())
                .toList();

        // Ẩn số điện thoại báo cáo nếu là USER khác và không phải chủ nhân ticket
        String reporterPhone = report.getReporter().getUserPhone();
        if (user.getUserRole() == UserRole.USER && !report.getReporter().getUserId().equals(user.getUserId())) {
            reporterPhone = "********";
        }

        return IncidentReportResponse.builder()
                .incidentId(report.getIncidentId())
                .title(report.getTitle())
                .description(report.getDescription())
                .incidentType(report.getIncidentType())
                .status(report.getStatus())
                .priority(report.getPriority())
                .resolutionNotes(report.getResolutionNotes())
                .locationDetails(report.getLocationDetails())
                .lostCardFee(report.getLostCardFee())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .resolvedAt(report.getResolvedAt())
                .cancelledAt(report.getCancelledAt())
                .cancellationReason(report.getCancellationReason())
                .reporterId(report.getReporter().getUserId())
                .reporterName(report.getReporter().getUserFullName())
                .reporterPhone(reporterPhone)
                .assignedStaffId(report.getAssignedStaff() != null ? report.getAssignedStaff().getUserId() : null)
                .assignedStaffName(report.getAssignedStaff() != null ? report.getAssignedStaff().getUserFullName() : null)
                .parkingBranchId(report.getParkingBranch().getParkingBranchId())
                .parkingBranchName(report.getParkingBranch().getBranchName())
                .parkingSessionId(report.getParkingSession() != null ? report.getParkingSession().getParkingSessionId() : null)
                .parkingCardId(report.getParkingCard() != null ? report.getParkingCard().getParkingCardId() : null)
                .cardCode(report.getParkingCard() != null ? report.getParkingCard().getCardCode() : null)
                .images(imgs)
                .logs(logs)
                .build();
    }
}
