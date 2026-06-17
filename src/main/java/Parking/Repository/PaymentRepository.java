package Parking.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import Parking.Model.Payment;

public interface PaymentRepository extends JpaRepository<Payment , Long> {

    boolean existsByParkingSessionParkingSessionId(
            Long parkingSessionId
    );

    Optional<Payment> findByParkingSessionParkingSessionId(
            Long parkingSessionId
    );
} 
