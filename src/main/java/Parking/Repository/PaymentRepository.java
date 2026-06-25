package Parking.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import Parking.Model.Payment;
import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment , Long> {

    boolean existsByParkingSessionParkingSessionId(
            Long parkingSessionId
    );

    Optional<Payment> findByParkingSessionParkingSessionId(
            Long parkingSessionId
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                    SELECT payment
                        FROM Payment payment
                        WHERE payment.transactionRef = :transactionRef
                    """)
        Optional<Payment> findByTransactionRefForUpdate(@Param("transactionRef")String transactionRef);
} 
