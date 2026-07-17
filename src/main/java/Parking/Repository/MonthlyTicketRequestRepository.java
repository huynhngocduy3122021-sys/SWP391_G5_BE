package Parking.Repository;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import Parking.Model.MonthlyTicketRequest;

@Repository
public interface MonthlyTicketRequestRepository extends JpaRepository<MonthlyTicketRequest, Long> {
    List<MonthlyTicketRequest> findByStatus(Integer status);
    List<MonthlyTicketRequest> findByUserUserId(Long userId);

    @Query("""
        SELECT request FROM MonthlyTicketRequest request
        JOIN request.payment payment
        WHERE request.vehicle.vehiclesId = :vehicleId
          AND request.parkingBranch.parkingBranchId = :branchId
          AND request.status IN (1, 2)
          AND payment.paymentStatus = Parking.enums.PaymentStatus.PAID
          AND (:issuedAt IS NULL OR request.createdAt <= :issuedAt)
        ORDER BY request.createdAt DESC
    """)
    List<MonthlyTicketRequest> findIssuedRequestCandidates(
            @Param("vehicleId") Long vehicleId,
            @Param("branchId") Long branchId,
            @Param("issuedAt") LocalDateTime issuedAt
    );

    default java.util.Optional<MonthlyTicketRequest> findBestIssuedRequestForTicket(
            Long vehicleId,
            Long branchId,
            LocalDateTime issuedAt
    ) {
        return findIssuedRequestCandidates(vehicleId, branchId, issuedAt)
                .stream()
                .findFirst();
    }

    @Query("""
        SELECT CASE WHEN COUNT(request) > 0 THEN true ELSE false END
        FROM MonthlyTicketRequest request
        WHERE request.user.userId = :userId
          AND request.status IN :statuses
    """)
    boolean existsOpenRequestByUser(
            @Param("userId") Long userId,
            @Param("statuses") java.util.Collection<Parking.enums.MonthlyTicketRequestStatus> statuses
    );

    @Query("""
        SELECT CASE WHEN COUNT(request) > 0 THEN true ELSE false END
        FROM MonthlyTicketRequest request
        WHERE request.renewalOfTicket.ticketId = :ticketId
          AND request.status IN :statuses
    """)
    boolean existsByRenewalOfTicketTicketIdAndStatusIn(
            @Param("ticketId") Long ticketId,
            @Param("statuses") java.util.Collection<Parking.enums.MonthlyTicketRequestStatus> statuses
    );
}
