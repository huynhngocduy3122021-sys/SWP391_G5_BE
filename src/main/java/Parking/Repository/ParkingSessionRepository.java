package Parking.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import Parking.Model.ParkingSession;
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

    long countByParkingBranchParkingBranchIdAndStatus(
            Long parkingBranchId,
            ParkingSessionStatus status
    );

    // khá phiên gửi xe để tránh 2 request cùng lúc
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParkingSession>
    findFirstByParkingCardCardCodeIgnoreCaseAndStatus(
            String cardCode,
            ParkingSessionStatus status
    );

    Optional<ParkingSession> findFirstByVehicleLicensePlateIgnoreCaseAndStatus(
            String licensePlate,
            ParkingSessionStatus status
    );
   
    
}