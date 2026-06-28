package Parking.dto.response;

import Parking.enums.IncidentPriority;
import Parking.enums.IncidentStatus;
import Parking.enums.IncidentType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class IncidentReportResponse {
    private Long incidentId;
    private String title;
    private String description;
    private IncidentType incidentType;
    private IncidentStatus status;
    private IncidentPriority priority;
    private String resolutionNotes;
    private String locationDetails;
    private BigDecimal lostCardFee;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;

    private Long reporterId;
    private String reporterName;
    private String reporterPhone;

    private Long assignedStaffId;
    private String assignedStaffName;

    private Long parkingBranchId;
    private String parkingBranchName;

    private Long parkingSessionId;
    private Long parkingCardId;
    private String cardCode;

    private List<IncidentImageResponse> images;
    private List<IncidentLogResponse> logs;

    @Getter
    @Setter
    @Builder
    public static class IncidentImageResponse {
        private Long incidentImageId;
        private String imageUrl;
        private LocalDateTime uploadedAt;
    }

    @Getter
    @Setter
    @Builder
    public static class IncidentLogResponse {
        private Long logId;
        private String changedByName;
        private LocalDateTime changedAt;
        private IncidentStatus oldStatus;
        private IncidentStatus newStatus;
        private String actionType;
        private String description;
    }
}
