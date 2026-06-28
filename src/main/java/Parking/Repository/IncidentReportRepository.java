package Parking.Repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import Parking.Model.IncidentReport;
import Parking.enums.IncidentPriority;
import Parking.enums.IncidentStatus;
import Parking.enums.IncidentType;

@Repository
public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {

    Page<IncidentReport> findByReporterUserId(Long reporterId, Pageable pageable);

    boolean existsByParkingSessionParkingSessionIdAndIncidentTypeAndStatusIn(
            Long sessionId, IncidentType type, List<IncidentStatus> activeStatuses);

    boolean existsByParkingCardParkingCardIdAndIncidentTypeAndStatusIn(
            Long cardId, IncidentType type, List<IncidentStatus> activeStatuses);

    @Query("""
        SELECT ir FROM IncidentReport ir
        WHERE (:branchId IS NULL OR ir.parkingBranch.parkingBranchId = :branchId)
          AND (:status IS NULL OR ir.status = :status)
          AND (:type IS NULL OR ir.incidentType = :type)
          AND (:priority IS NULL OR ir.priority = :priority)
          AND (:startDate IS NULL OR ir.createdAt >= :startDate)
          AND (:endDate IS NULL OR ir.createdAt <= :endDate)
          AND (:assignedStaffId IS NULL OR ir.assignedStaff.userId = :assignedStaffId)
    """)
    Page<IncidentReport> findByFilters(
            @Param("branchId") Long branchId,
            @Param("status") IncidentStatus status,
            @Param("type") IncidentType type,
            @Param("priority") IncidentPriority priority,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("assignedStaffId") Long assignedStaffId,
            Pageable pageable
    );
}
