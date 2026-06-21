package Parking.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import Parking.Model.VehicleImage;

@Repository
public interface VehicleImageRepository extends JpaRepository<VehicleImage ,Long>{

    List<VehicleImage> findByParkingSessionParkingSessionIdOrderByUploadedAtAsc(Long parkingSessionId);
    
}
