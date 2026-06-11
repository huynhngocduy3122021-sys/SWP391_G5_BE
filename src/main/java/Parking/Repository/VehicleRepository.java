package Parking.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import Parking.Model.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle,Long> {
    Optional<Vehicle> findByLicensePlate(String licensePlate);
    
} 
