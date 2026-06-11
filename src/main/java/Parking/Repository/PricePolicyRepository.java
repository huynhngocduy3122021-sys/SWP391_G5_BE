package Parking.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import Parking.Model.PricePolicy;
import Parking.Model.VehicleType;

public interface PricePolicyRepository extends JpaRepository<PricePolicy,Long> {
    Optional<PricePolicy> findFirstByVehicleTypeAndActiveTrue(VehicleType vehicleType);
}

  
