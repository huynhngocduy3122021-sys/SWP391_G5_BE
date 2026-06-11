package Parking.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import Parking.Model.Payment;

public interface PaymentRepository extends JpaRepository<Payment , Long> {

    
} 
