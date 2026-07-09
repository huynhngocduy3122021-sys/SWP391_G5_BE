package Parking.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import Parking.Model.PricePolicy;
import Parking.Model.VehicleType;

public interface PricePolicyRepository extends JpaRepository<PricePolicy,Long> {
    Optional<PricePolicy>
    findFirstByVehicleTypeVehicleTypeIdAndActiveTrueOrderByPricePolicyIdDesc(
            Long vehicleTypeId
    );

    @org.springframework.data.jpa.repository.Query("SELECT p FROM PricePolicy p WHERE p.vehicleType.vehicleTypeId = :vehicleTypeId AND p.active = true AND LOWER(p.policyName) NOT LIKE '%gói%' AND LOWER(p.policyName) NOT LIKE '%tháng%' AND p.policyName NOT LIKE '[Gói%' ORDER BY p.pricePolicyId DESC")
    java.util.List<PricePolicy> findActiveHourlyPolicies(@org.springframework.data.repository.query.Param("vehicleTypeId") Long vehicleTypeId);

    default Optional<PricePolicy> findFirstActiveHourlyPolicy(Long vehicleTypeId) {
        return findActiveHourlyPolicies(vehicleTypeId).stream().findFirst();
    }
}
