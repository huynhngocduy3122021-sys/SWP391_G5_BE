package Parking.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import Parking.Model.ParkingFloor;

public interface ParkingFloorRepository extends JpaRepository<ParkingFloor , Long> {

    List<ParkingFloor>findByParkingBranchParkingBranchIdOrderByFloorNumberAsc(Long parkingBranchId);

    boolean existsByParkingBranchParkingBranchIdAndFloorNumber(Long parkingBranchId,Integer floorNumber);

    boolean existsByParkingBranchParkingBranchIdAndFloorNumberAndParkingFloorIdNot(Long parkingBranchId,Integer floorNumber,Long parkingFloorId);
}