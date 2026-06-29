package Parking.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import Parking.Model.Booking;
import Parking.enums.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.parkingBranch.parkingBranchId = :branchId
          AND b.vehicleType.vehicleTypeId = :vehicleTypeId
          AND b.status = 'CONFIRMED'
          AND b.holdUntil > :now
    """)
    long countActiveBookings(
            @Param("branchId") Long branchId,
            @Param("vehicleTypeId") Long vehicleTypeId,
            @Param("now") LocalDateTime now
    );

    boolean existsByUserUserIdAndStatusIn(Long userId, List<BookingStatus> statuses);

    long countByUserUserIdAndStatusIn(Long userId, List<BookingStatus> statuses);

    boolean existsByVehicleVehiclesIdAndStatusIn(Long vehicleId, List<BookingStatus> statuses);

    List<Booking> findByVehicleLicensePlateIgnoreCaseAndStatus(String licensePlate, BookingStatus status);

    List<Booking> findByStatusAndHoldUntilBefore(BookingStatus status, LocalDateTime dateTime);

    java.util.Optional<Booking> findByBookingCodeIgnoreCase(String bookingCode);
}
