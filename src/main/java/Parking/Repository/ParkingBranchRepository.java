package Parking.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import Parking.Model.ParkingBranch;


public interface ParkingBranchRepository extends JpaRepository<ParkingBranch , Long>{
     
    Optional<ParkingBranch> findByParkingBranchId(Long parkingBranchId);
    boolean existsByBranchNameIgnoreCase(String branchName);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT b FROM ParkingBranch b WHERE b.parkingBranchId = :id")
    Optional<ParkingBranch> findAndLockByParkingBranchId(@org.springframework.data.repository.query.Param("id") Long id);

    
    
}
