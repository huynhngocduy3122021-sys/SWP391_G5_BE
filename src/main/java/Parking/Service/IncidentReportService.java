package Parking.Service;

import java.math.BigDecimal;
import Parking.Model.IncidentLog;
import Parking.Model.IncidentReport;
import Parking.Model.ParkingBranch;
import Parking.Model.ParkingCard;
import Parking.Model.ParkingSession;
import Parking.Model.User;
import Parking.Repository.IncidentReportRepository;
import Parking.Repository.ParkingBranchRepository;
import Parking.Repository.ParkingCardRepository;
import Parking.Repository.ParkingSessionRepository;
import Parking.Repository.UserRepository;
import Parking.dto.request.AssignIncidentRequest;
import Parking.dto.request.CancelIncidentRequest;
import Parking.dto.request.CreateIncidentRequest;
import Parking.dto.request.LostCardIncidentRequest;
import Parking.dto.request.ResolveIncidentRequest;
import Parking.dto.response.IncidentReportResponse;
import Parking.enums.IncidentLogAction;
import Parking.enums.IncidentPriority;
import Parking.enums.IncidentStatus;
import Parking.enums.IncidentType;
import Parking.enums.LostCardStage;
import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingCardType;
import Parking.enums.ParkingSessionStatus;
import Parking.enums.UserRole;
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
    private final Parking.Repository.MonthlyTicketRepository monthlyTicketRepository;

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

            // Báo mất thẻ tháng phải đi qua API /lost-card để bắt buộc
            // chính chủ tài khoản USER khai báo và hỗ trợ cả trường hợp
            // xe chưa vào bãi (parkingSessionId = null).
            if (card.getType() == ParkingCardType.MONTHLY) {
                throw new ParkingSessionException(
                        "Báo mất thẻ tháng phải được thực hiện bởi chính chủ qua API /api/incidents/lost-card");
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

        IncidentStatus initialStatus = IncidentStatus.PENDING;

        // 3. Khởi tạo đối tượng IncidentReport
        IncidentReport report = new IncidentReport();
        report.setTitle(request.getTitle());
        report.setDescription(request.getDescription());
        report.setIncidentType(request.getIncidentType());
        report.setPriority(request.getPriority());
        report.setStatus(initialStatus);
        report.setReporter(reporter);
        report.setParkingBranch(branch);
        report.setParkingSession(session);
        report.setParkingCard(card);
        if (request.getIncidentType() == IncidentType.LOST_CARD) {
            report.setLostCardFee(BigDecimal.ZERO);
        }
        report.setLocationDetails(request.getLocationDetails());
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        // 4. (Đã chuyển xử lý ảnh sang luồng upload riêng biệt)

        // 5. Lưu vết nhật ký khởi tạo (Audit Log)
        IncidentLog initialLog = new IncidentLog();
        initialLog.setChangedBy(reporter);
        initialLog.setChangedAt(LocalDateTime.now());
        initialLog.setOldStatus(null);
        initialLog.setNewStatus(initialStatus);
        initialLog.setActionType(IncidentLogAction.CREATE);
        initialLog.setDescription("Sự cố đã được khởi tạo bởi " + reporter.getUserFullName());
        report.addLog(initialLog);

        return convertToResponse(incidentReportRepository.save(report));
    }

    @Transactional
    public IncidentReportResponse reportLostCard(LostCardIncidentRequest request) {
        User reporter = getCurrentUser();

        ParkingSession session = null;
        ParkingBranch branch;
        ParkingCard card;

        if (request.getParkingSessionId() != null) {
            session = parkingSessionRepository.findById(request.getParkingSessionId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy phiên giữ xe đang hoạt động"));

            if (session.getStatus() != ParkingSessionStatus.ACTIVE) {
                throw new ParkingSessionException("Phiên giữ xe này đã kết thúc, không thể báo mất thẻ!");
            }

            branch = session.getParkingBranch();
            if (request.getParkingBranchId() != null
                    && !request.getParkingBranchId().equals(branch.getParkingBranchId())) {
                throw new ParkingSessionException("Chi nhánh không khớp với phiên gửi xe");
            }
            card = session.getParkingCard();
        } else {
            if (request.getParkingCardId() == null
                    && (request.getCardCode() == null || request.getCardCode().isBlank())) {
                throw new ParkingSessionException("Khi xe chưa vào bãi, cần cung cấp parkingCardId hoặc cardCode");
            }
            if (request.getParkingBranchId() == null) {
                throw new ParkingSessionException("Khi xe chưa vào bãi, cần cung cấp parkingBranchId");
            }

            card = request.getParkingCardId() != null
                    ? parkingCardRepository.findByIdForUpdate(request.getParkingCardId())
                        .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thẻ giữ xe"))
                    : parkingCardRepository.findByCardCodeIgnoreCase(request.getCardCode().trim())
                        .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thẻ giữ xe"));

            branch = parkingBranchRepository.findById(request.getParkingBranchId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy chi nhánh bãi xe"));
            if (card.getParkingBranch() == null
                    || !card.getParkingBranch().getParkingBranchId().equals(branch.getParkingBranchId())) {
                throw new ParkingSessionException("Thẻ giữ xe không thuộc chi nhánh đã chọn");
            }

            if (card.getType() != ParkingCardType.MONTHLY) {
                throw new ParkingSessionException("Chỉ thẻ tháng có thể báo mất khi xe chưa vào bãi");
            }

            boolean belongsToReporter = monthlyTicketRepository.findActiveTicketsByCardAndUser(
                    card.getParkingCardId(), reporter.getUserId(), LocalDateTime.now()).stream().anyMatch(ticket ->
                            ticket.getVehicle() != null && ticket.getVehicle().getUser() != null
                                    && reporter.getUserId().equals(ticket.getVehicle().getUser().getUserId()));
            if (!belongsToReporter) {
                throw new ParkingSessionException("Thẻ tháng không thuộc tài khoản đang đăng nhập hoặc đã hết hiệu lực");
            }
        }

        if (card == null) {
            throw new ParkingSessionException("Không tìm thấy thẻ giữ xe đi kèm với phiên này!");
        }

        // Thẻ tháng luôn phải do chính chủ USER tự khai báo.
        // STAFF chỉ được tạo báo cáo cho thẻ guest/regular đang có phiên trong bãi.
        if (card.getType() == ParkingCardType.MONTHLY
                && reporter.getUserRole() != UserRole.USER) {
            throw new ParkingSessionException(
                    "Báo mất thẻ tháng phải được chính chủ USER thực hiện");
        }

        ParkingCardType cardType = request.getCardType() != null ? request.getCardType() : card.getType();
        if (card.getType() != cardType) {
            throw new ParkingSessionException("Loại thẻ không khớp với thẻ được xác minh");
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
        report.setParkingBranch(branch);
        report.setParkingSession(session);
        report.setParkingCard(card);
        report.setCardType(cardType);
        report.setLostCardFee(BigDecimal.ZERO);
        report.setLostStage(request.getLostStage() != null
                ? request.getLostStage()
                : session != null ? LostCardStage.INSIDE_PARKING : LostCardStage.BEFORE_ENTRY);
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        // Ghi Log Audit
        IncidentLog log = new IncidentLog();
        log.setChangedBy(reporter);
        log.setChangedAt(LocalDateTime.now());
        log.setOldStatus(null);
        log.setNewStatus(report.getStatus());
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
    public IncidentReportResponse verifyLostCard(Long id) {
        User operator = getCurrentUser();
        IncidentReport report = incidentReportRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy báo cáo sự cố"));

        if (report.getIncidentType() != IncidentType.LOST_CARD) {
            throw new ParkingSessionException("Sự cố này không phải báo mất thẻ");
        }
        branchScopeService.assertSameBranch(report.getParkingBranch().getParkingBranchId());
        if (report.getReporter() == null || report.getParkingCard() == null) {
            throw new ParkingSessionException("Báo cáo thiếu thông tin chủ thẻ hoặc thẻ xe");
        }

        if (report.getParkingCard().getType() == ParkingCardType.MONTHLY) {
            throw new ParkingSessionException(
                    "Báo cáo mất thẻ tháng không cần manager xác minh; user phải thanh toán VNPay trước");
        }

        if (report.getStatus() != IncidentStatus.PENDING) {
            throw new ParkingSessionException("Báo mất thẻ không còn ở trạng thái chờ xác minh");
        }

        IncidentStatus oldStatus = report.getStatus();
        report.setLostCardFee(BigDecimal.ZERO);
        report.setStatus(IncidentStatus.IN_PROGRESS);
        report.setUpdatedAt(LocalDateTime.now());

        IncidentLog log = new IncidentLog();
        log.setChangedBy(operator);
        log.setChangedAt(LocalDateTime.now());
        log.setOldStatus(oldStatus);
        log.setNewStatus(IncidentStatus.IN_PROGRESS);
        log.setActionType(IncidentLogAction.UPDATE_STATUS);
        log.setDescription("Đã xác minh báo mất thẻ; chuyển sang chờ checkout/cấp thẻ tháng mới.");
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
        if (report.getIncidentType() == IncidentType.LOST_CARD
                && report.getParkingCard() != null
                && report.getParkingCard().getType() == ParkingCardType.MONTHLY) {
            if (report.getStatus() != IncidentStatus.IN_PROGRESS) {
                throw new ParkingSessionException("Báo cáo phải được manager xác minh trước khi hoàn tất");
            }

            ParkingSession session = report.getParkingSession();
            if (session != null && session.getStatus() == ParkingSessionStatus.ACTIVE) {
                // Yêu cầu check-out xe trước khi resolve ticket báo mất thẻ
                throw new ParkingSessionException("Không thể hoàn tất sự cố mất thẻ khi phiên gửi xe của phương tiện vẫn đang hoạt động. Vui lòng thực hiện Check-out xe trước.");
            }

            if (report.getReplacementCard() == null) {
                throw new ParkingSessionException("Phải cấp thẻ tháng thay thế trước khi hoàn tất report");
            }
        }

        IncidentStatus oldStatus = report.getStatus();
        report.setStatus(IncidentStatus.RESOLVED);
        report.setResolutionNotes(request.getResolutionNotes());
        if (report.getIncidentType() == IncidentType.LOST_CARD
                && report.getParkingCard() != null
                && report.getParkingCard().getType() == ParkingCardType.MONTHLY) {
            report.setLostCardFee(BigDecimal.ZERO);
        } else if (request.getLostCardFee() != null) {
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

        if (report.getPayment() != null
                && report.getPayment().getPaymentStatus() == Parking.enums.PaymentStatus.PAID) {
            throw new ParkingSessionException("Không thể hủy báo cáo đã thanh toán");
        }

        boolean managerOrAdmin = operator.getUserRole() == UserRole.MANAGER
                || operator.getUserRole() == UserRole.ADMIN;
        if (!managerOrAdmin) {
            if (operator.getUserRole() != UserRole.USER
                    || report.getReporter() == null
                    || !operator.getUserId().equals(report.getReporter().getUserId())) {
                throw new ParkingSessionException("Bạn không có quyền hủy báo cáo này");
            }
        }

        // Kiểm tra quyền chi nhánh: Admin được thao tác toàn bộ, Manager/Staff chỉ thao tác nhánh của mình
        if (managerOrAdmin) {
            branchScopeService.assertSameBranch(report.getParkingBranch().getParkingBranchId());
        }

        if (report.getReplacementCard() != null) {
            throw new ParkingSessionException("Không thể hủy báo cáo đã cấp thẻ thay thế");
        }

        if (report.getPayment() != null
                && report.getPayment().getPaymentStatus() != Parking.enums.PaymentStatus.PAID) {
            report.getPayment().setPaymentStatus(Parking.enums.PaymentStatus.CANCELLED);
        }

        IncidentStatus oldStatus = report.getStatus();
        report.setStatus(IncidentStatus.CANCELLED);
        report.setCancellationReason(request.getCancellationReason());
        report.setCancelledAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());

        // Thêm chặn hủy báo mất nếu đã cấp thẻ thay thế
        if (report.getReplacementCard() != null) {
            throw new ParkingSessionException("Không thể hủy báo mất sau khi đã cấp thẻ thay thế");
        }

        // Rollback trạng thái của thẻ xe khi hủy báo mất thẻ (nếu có)
        if (report.getIncidentType() == IncidentType.LOST_CARD && report.getParkingCard() != null) {
            ParkingCard card = report.getParkingCard();
            ParkingSession session = report.getParkingSession();
            if (session != null && session.getStatus() == ParkingSessionStatus.ACTIVE) {
                // Rollback thẻ về trạng thái đang sử dụng nếu session còn kích hoạt
                card.setStatus(ParkingCardStatus.IN_USE);
                parkingCardRepository.save(card);
            } else {
                card.setStatus(ParkingCardStatus.AVAILABLE);
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

    public IncidentReportResponse convertToResponse(IncidentReport report) {
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
                .paymentId(report.getPayment() != null ? report.getPayment().getPaymentId() : null)
                .paymentStatus(report.getPayment() != null ? report.getPayment().getPaymentStatus() : null)
                .paymentMethod(report.getPayment() != null ? report.getPayment().getPaymentMethod() : null)
                .paymentTransactionRef(report.getPayment() != null ? report.getPayment().getTransactionRef() : null)
                .paymentPaidAt(report.getPayment() != null ? report.getPayment().getPaidAt() : null)
                .paymentCashReceiptNumber(report.getPayment() != null
                        ? report.getPayment().getCashReceiptNumber() : null)
                .paymentCashCollectedByUserId(report.getPayment() != null
                        && report.getPayment().getCashCollectedBy() != null
                        ? report.getPayment().getCashCollectedBy().getUserId() : null)
                .paymentCashCollectedAt(report.getPayment() != null
                        ? report.getPayment().getCashCollectedAt() : null)
                .paymentCashVerifiedByUserId(report.getPayment() != null
                        && report.getPayment().getCashVerifiedBy() != null
                        ? report.getPayment().getCashVerifiedBy().getUserId() : null)
                .paymentCashVerifiedAt(report.getPayment() != null
                        ? report.getPayment().getCashVerifiedAt() : null)
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
                .parkingCardType(report.getParkingCard() != null && report.getParkingCard().getType() != null ? report.getParkingCard().getType().name() : null)
                .cardType(report.getCardType())
                .lostStage(report.getLostStage())
                .replacementCardId(report.getReplacementCard() != null ? report.getReplacementCard().getParkingCardId() : null)
                .replacementCardCode(report.getReplacementCard() != null ? report.getReplacementCard().getCardCode() : null)
                .replacementTicketId(report.getReplacementTicket() != null ? report.getReplacementTicket().getTicketId() : null)
                .replacementAt(report.getReplacementAt())
                .replacementByUserId(report.getReplacementBy() != null ? report.getReplacementBy().getUserId() : null)
                .replacementByName(report.getReplacementBy() != null ? report.getReplacementBy().getUserFullName() : null)
                .monthlyCardReplacementRequired(
                    report.getIncidentType() == IncidentType.LOST_CARD &&
                    report.getParkingCard() != null &&
                    report.getReplacementCard() == null &&
                    monthlyTicketRepository.findActiveTicketsForLostCard(report.getParkingCard().getParkingCardId(), LocalDateTime.now()).size() > 0
                )
                .images(imgs)
                .logs(logs)
                .build();
    }
}
