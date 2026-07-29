package Parking.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import Parking.Model.MonthlyTicket;
import java.time.LocalDateTime;
import java.util.List;

public interface MonthlyTicketRepository extends JpaRepository<MonthlyTicket, Long> {

    @Query("""
        SELECT COUNT(mt) > 0 FROM MonthlyTicket mt
        WHERE mt.monthlyTicketRequest.id = :requestId
    """)
    boolean existsByMonthlyTicketRequestId(@Param("requestId") Long requestId);

    @Query("""
        SELECT mt FROM MonthlyTicket mt
        WHERE :branchId IS NULL
           OR mt.parkingCard.parkingBranch.parkingBranchId = :branchId
    """)
    List<MonthlyTicket> findAllByBranchId(@Param("branchId") Long branchId);

    @Query("""
        SELECT COUNT(mt) > 0 FROM MonthlyTicket mt
        WHERE mt.vehicle.vehiclesId = :vehicleId
          AND mt.status = 1
          AND (:ticketId IS NULL OR mt.ticketId <> :ticketId)
          AND mt.startDate < :endDate
          AND mt.endDate > :startDate
    """)
    boolean existsActiveOverlapByVehicle(
        @Param("vehicleId") Long vehicleId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("ticketId") Long ticketId
    );

    @Query("""
        SELECT COUNT(mt) > 0 FROM MonthlyTicket mt
        WHERE mt.parkingCard.parkingCardId = :parkingCardId
          AND mt.status = 1
          AND (:ticketId IS NULL OR mt.ticketId <> :ticketId)
          AND mt.startDate < :endDate
          AND mt.endDate > :startDate
    """)
    boolean existsActiveOverlapByCard(
        @Param("parkingCardId") Long parkingCardId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("ticketId") Long ticketId
    );

    @Query("""
        SELECT COUNT(mt) > 0 FROM MonthlyTicket mt
        WHERE mt.parkingCard.parkingCardId = :parkingCardId
          AND mt.status = 1
          AND mt.startDate <= :time
          AND mt.endDate >= :time
    """)
    boolean existsActiveTicketByCard(
        @Param("parkingCardId") Long parkingCardId,
        @Param("time") LocalDateTime time
    );

    @Query("""
        SELECT mt FROM MonthlyTicket mt
        WHERE mt.parkingCard.parkingCardId = :parkingCardId
          AND mt.status = 1
          AND mt.startDate <= :time
          AND mt.endDate >= :time
    """)
    List<MonthlyTicket> findActiveTicketsByCard(
        @Param("parkingCardId") Long parkingCardId,
        @Param("time") LocalDateTime time
    );

    default java.util.Optional<MonthlyTicket> findActiveTicketByCard(Long parkingCardId, LocalDateTime time) {
        return findActiveTicketsByCard(parkingCardId, time).stream().findFirst();
    }

    @Query("""
        SELECT mt FROM MonthlyTicket mt
        WHERE mt.vehicle.user.userId = :userId
    """)
    List<MonthlyTicket> findAllByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT COUNT(mt) > 0 FROM MonthlyTicket mt
        WHERE mt.vehicle.user.userId = :userId
          AND mt.parkingCard.type = 'EMPLOYEE'
          AND mt.status = 1
          AND (:ticketId IS NULL OR mt.ticketId <> :ticketId)
    """)
    boolean existsActiveEmployeeTicketByUserId(
        @Param("userId") Long userId, 
        @Param("ticketId") Long ticketId
    );

    java.util.Optional<MonthlyTicket> findFirstByVehicleVehiclesIdOrderByEndDateDesc(Long vehicleId);

    @Query("""
        SELECT COUNT(mt) > 0 FROM MonthlyTicket mt
        WHERE mt.vehicle.vehiclesId = :vehicleId
          AND mt.status = 1
          AND mt.startDate <= :time
          AND mt.endDate >= :time
    """)
    boolean existsActiveTicketByVehicle(
        @Param("vehicleId") Long vehicleId,
        @Param("time") LocalDateTime time
    );
}
