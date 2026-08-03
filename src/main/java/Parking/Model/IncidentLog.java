package Parking.Model;

import Parking.enums.IncidentLogAction;
import Parking.enums.IncidentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Entity lưu trữ Lịch sử xử lý sự cố.
 * Giống như "Nhật ký", ghi lại việc ai đã đổi trạng thái của sự cố vào lúc nào.
 */
@Entity
@Getter
@Setter
@Table(name = "incident_logs")
public class IncidentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id", nullable = false)
    private User changedBy; // Ai thực hiện thao tác này?

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now(); // Thực hiện lúc nào?

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status")
    private IncidentStatus oldStatus; // Trạng thái cũ trước khi đổi

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private IncidentStatus newStatus; // Trạng thái mới sau khi đổi

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private IncidentLogAction actionType; // Loại hành động (Ví dụ: UPLOAD_IMAGE, UPDATE_STATUS)

    @Column(name = "description", nullable = false, columnDefinition = "nvarchar(max)")
    private String description; // Chi tiết thao tác (Ví dụ: "Nhân viên Nguyễn Văn A đã xác nhận đền bù 50k")

    // Lịch sử này thuộc về Báo cáo sự cố nào?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private IncidentReport incidentReport;
}
