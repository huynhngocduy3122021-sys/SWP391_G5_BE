package Parking.Repository;

import java.util.Optional;
import java.time.LocalDateTime;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import Parking.Model.ParkingCard;
import Parking.enums.MonthlyTicketStatus;
import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingCardType;
import java.util.List;

public interface ParkingCardRepository extends JpaRepository<ParkingCard , Long> {
    Optional<ParkingCard> findByCardCodeIgnoreCase(String cardCode);

    boolean existsByCardCode(String cardCode);

    List<ParkingCard> findByParkingBranchParkingBranchId(Long parkingBranchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParkingCard> findByParkingCardId(Long parkingCardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT pc FROM ParkingCard pc
        WHERE pc.parkingBranch.parkingBranchId = :branchId
          AND pc.status = :status
          AND pc.type = :type
          AND NOT EXISTS (
              SELECT mt.ticketId FROM MonthlyTicket mt
              WHERE mt.parkingCard = pc
                AND mt.status = :ticketStatus
                AND mt.startDate <= :time
                AND mt.endDate >= :time
          )
        ORDER BY pc.parkingCardId ASC
    """)
    List<ParkingCard> findAvailableMonthlyCardsWithoutActiveTicket(
        @Param("branchId") Long branchId,
        @Param("status") ParkingCardStatus status,
        @Param("type") ParkingCardType type,
        @Param("ticketStatus") MonthlyTicketStatus ticketStatus,
        @Param("time") LocalDateTime time,
        Pageable pageable
    );

    default Optional<ParkingCard> findFirstAvailableMonthlyCard(Long branchId) {
        return findAvailableMonthlyCardsWithoutActiveTicket(
                branchId,
                ParkingCardStatus.AVAILABLE,
                ParkingCardType.MONTHLY,
                MonthlyTicketStatus.ACTIVE,
                LocalDateTime.now(),
                PageRequest.of(0, 1)
        ).stream().findFirst();
    }
}
