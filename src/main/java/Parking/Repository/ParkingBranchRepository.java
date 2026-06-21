package Parking.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import Parking.Model.ParkingBranch;


public interface ParkingBranchRepository extends JpaRepository<ParkingBranch , Long>{
     
    Optional<ParkingBranch> findByParkingBranchId(Long parkingBranchId);
    boolean existsByBranchNameIgnoreCase(String branchName);

    
    
}
