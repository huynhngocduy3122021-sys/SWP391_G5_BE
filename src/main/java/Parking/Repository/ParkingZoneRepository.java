package Parking.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import Parking.Model.ParkingZone;
public interface ParkingZoneRepository extends JpaRepository<ParkingZone,Long> {
    
    
}
