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

    /**
     * Tính tổng sức chứa của các khu vực đỗ xe đang hoạt động trong một chi nhánh,
     * nhưng chỉ dành cho loại phương tiện được chỉ định.
     * Dùng khi cần thống kê số chỗ theo từng loại xe (ô tô, xe máy,...).
     */
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

    /**
     * Tính tổng sức chứa của tất cả khu vực đỗ xe đang hoạt động trong một chi nhánh,
     * không phân biệt loại phương tiện.
     * Dùng khi cần thống kê tổng số chỗ của toàn chi nhánh.
     */
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
