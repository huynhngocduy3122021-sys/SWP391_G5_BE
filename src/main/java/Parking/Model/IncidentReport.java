package Parking.Model;

import Parking.enums.IncidentPriority;
import Parking.enums.IncidentStatus;
import Parking.enums.IncidentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity đại diện cho một Báo cáo sự cố (Ví dụ: Khách báo mất thẻ, Mất điện bãi xe, Xe bị xước).
 */
@Entity
@Getter
@Setter
@Table(name = "incident_reports")
public class IncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incident_id")
    private Long incidentId; // Mã sự cố

    @Column(name = "title", nullable = false, columnDefinition = "nvarchar(255)")
    private String title; // Tiêu đề sự cố

    @Column(name = "description", nullable = false, columnDefinition = "nvarchar(max)")
    private String description; // Mô tả chi tiết

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false)
    private IncidentType incidentType; // Phân loại (Mất thẻ, Hỏng kỹ thuật...)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncidentStatus status = IncidentStatus.PENDING; // Trạng thái xử lý (PENDING, IN_PROGRESS, RESOLVED)

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private IncidentPriority priority = IncidentPriority.MEDIUM; // Mức độ ưu tiên

    @Column(name = "resolution_notes", columnDefinition = "nvarchar(max)")
    private String resolutionNotes; // Ghi chú kết quả sau khi giải quyết xong

    @Column(name = "location_details", columnDefinition = "nvarchar(500)")
    private String locationDetails; // Vị trí xảy ra sự cố trong bãi

    @Column(name = "lost_card_fee")
    private BigDecimal lostCardFee = BigDecimal.ZERO; // Phí phạt đền thẻ (Nếu sự cố là mất thẻ)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(); // Giờ báo cáo

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt; // Giờ giải quyết xong

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "nvarchar(max)")
    private String cancellationReason; // Lý do hủy báo cáo

    // --- CÁC MỐI QUAN HỆ ---
    
    // Ai là người báo cáo? (Khách hàng hoặc nhân viên)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    // Quản lý phân công cho Nhân viên nào đi xử lý?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id")
    private User assignedStaff;

    // Xảy ra ở chi nhánh nào?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_branch_id", nullable = false)
    private ParkingBranch parkingBranch;

    // Gắn với lượt gửi xe nào? (Nếu có)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_session_id")
    private ParkingSession parkingSession;

    // Gắn với thẻ giữ xe nào? (Thường dùng cho báo mất thẻ)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_card_id")
    private ParkingCard parkingCard; 

    // Gắn với hóa đơn đền bù nào? (Nếu có phát sinh tiền đền)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment; 

    // Nhật ký các bước xử lý sự cố (Lưu lịch sử)
    @OneToMany(mappedBy = "incidentReport", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IncidentLog> incidentLogs = new ArrayList<>();

    // Các hình ảnh bằng chứng khách/nhân viên chụp lại
    @OneToMany(mappedBy = "incidentReport", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IncidentImage> incidentImages = new ArrayList<>();

    public void addLog(IncidentLog log) {
        incidentLogs.add(log);
        log.setIncidentReport(this);
    }

    public void addImage(IncidentImage image) {
        incidentImages.add(image);
        image.setIncidentReport(this);
    }
}
