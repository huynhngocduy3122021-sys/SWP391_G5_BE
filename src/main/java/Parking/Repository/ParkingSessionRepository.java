package Parking.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import Parking.Model.ParkingBranch;
import Parking.Model.ParkingCard;
import Parking.Model.ParkingSession;
import Parking.Model.Vehicle;
import java.util.Optional;
import Parking.enums.ParkingSessionStatus;
import jakarta.persistence.LockModeType;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession,Long>{
     boolean existsByVehicleVehiclesIdAndStatus(
            Long vehicleId,
            ParkingSessionStatus status
    );

    boolean existsByParkingCardParkingCardIdAndStatus(
            Long parkingCardId,
            ParkingSessionStatus status
    );

    long countByParkingBranchParkingBranchIdAndVehicleVehicleTypeVehicleTypeIdAndStatus(
            Long parkingBranchId,
            Long vehicleTypeId,
            ParkingSessionStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParkingSession>
    findFirstByParkingCardCardCodeIgnoreCaseAndStatus(
            String cardCode,
            ParkingSessionStatus status
    );
    
}