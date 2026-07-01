package Parking.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import Parking.Model.ParkingZone;
public interface ParkingZoneRepository extends JpaRepository<ParkingZone,Long> {
    
    boolean existsByParkingFloorParkingFloorId(Long parkingFloorId);

    boolean existsByParkingFloorParkingFloorIdAndParkingZoneIdNot(Long parkingFloorId,Long parkingZoneId);

    List<ParkingZone>findByParkingFloorParkingBranchParkingBranchId(Long parkingBranchId);

    @Query("""
        SELECT COALESCE(SUM(zone.capacity), 0)
        FROM ParkingZone zone
        WHERE zone.parkingFloor.parkingBranch.parkingBranchId = :branchId
          AND zone.vehicleType.vehicleTypeId = :vehicleTypeId
          AND zone.active = true
          AND zone.parkingFloor.active = true
          AND zone.parkingFloor.parkingBranch.active = true
    """)
    Long calculateTotalCapacity(
            @Param("branchId") Long branchId,
            @Param("vehicleTypeId") Long vehicleTypeId
    );

    @Query("""
        SELECT COALESCE(SUM(zone.capacity), 0)
        FROM ParkingZone zone
        WHERE zone.parkingFloor.parkingBranch.parkingBranchId = :branchId
          AND zone.active = true
          AND zone.parkingFloor.active = true
          AND zone.parkingFloor.parkingBranch.active = true
    """)
    Long calculateTotalCapacityByBranch(
            @Param("branchId") Long branchId
    );
}
