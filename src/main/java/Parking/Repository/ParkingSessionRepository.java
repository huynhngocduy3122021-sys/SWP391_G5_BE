package Parking.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import Parking.Model.ParkingCard;
import Parking.Model.ParkingSession;
import Parking.Model.Vehicle;
import java.util.Optional;
import Parking.enums.ParkingSessionStatus;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession,Long>{
    Optional<ParkingSession> findByParkingCardAndStatus( ParkingCard parkingCard, ParkingSessionStatus status);
    

    boolean existsByVehicleAndStatus(Vehicle vehicle,ParkingSessionStatus status);
}
