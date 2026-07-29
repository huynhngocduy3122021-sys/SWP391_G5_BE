package Parking.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import Parking.Model.Vehicle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle,Long> {
   Optional<Vehicle> findByLicensePlateIgnoreCase(
            String licensePlate
    );

    boolean existsByLicensePlateIgnoreCase(
            String licensePlate
    );
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT v
        FROM Vehicle v
        WHERE UPPER(v.licensePlate) = UPPER(:licensePlate)
        """)
    Optional<Vehicle> findByLicensePlateForUpdate(
            @Param("licensePlate") String licensePlate
    );
}
