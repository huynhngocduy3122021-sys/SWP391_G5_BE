package Parking.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import Parking.Model.PricePolicy;
import org.springframework.data.repository.query.Param;
public interface PricePolicyRepository extends JpaRepository<PricePolicy,Long> {
    Optional<PricePolicy>
    findFirstByVehicleTypeVehicleTypeIdAndActiveTrueOrderByPricePolicyIdDesc(
            Long vehicleTypeId
    );

    @Query("SELECT p FROM PricePolicy p WHERE p.vehicleType.vehicleTypeId = :vehicleTypeId AND p.active = true AND LOWER(p.policyName) NOT LIKE '%gói%' AND LOWER(p.policyName) NOT LIKE '%tháng%' AND p.policyName NOT LIKE '[Gói%' ORDER BY p.pricePolicyId DESC")
    java.util.List<PricePolicy> findActiveHourlyPolicies(@Param("vehicleTypeId") Long vehicleTypeId);

    default Optional<PricePolicy> findFirstActiveHourlyPolicy(Long vehicleTypeId) {
        return findActiveHourlyPolicies(vehicleTypeId).stream().findFirst();
    }
}
