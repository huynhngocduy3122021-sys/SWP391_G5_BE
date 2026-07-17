package Parking.Repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import Parking.Model.ParkingBranch;


public interface ParkingBranchRepository extends JpaRepository<ParkingBranch , Long>{
     
    boolean existsByBranchNameIgnoreCase(String branchName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ParkingBranch b WHERE b.parkingBranchId = :id")
    Optional<ParkingBranch> findAndLockByParkingBranchId(@Param("id") Long id);

    
    
}
