package Parking.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import Parking.Model.VehicleType;

public interface VehicleTypeRepository extends JpaRepository<VehicleType,Long> {

    
}
