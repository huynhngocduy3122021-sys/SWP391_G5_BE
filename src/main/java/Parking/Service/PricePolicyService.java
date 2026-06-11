package Parking.Service;

import org.springframework.stereotype.Service;

import Parking.Model.PricePolicy;
import Parking.Model.VehicleType;
import Parking.Repository.PricePolicyRepository;
import Parking.Repository.VehicleTypeRepository;
import Parking.dto.request.CreatePricePolicyRequest;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricePolicyService {
     private final PricePolicyRepository pricePolicyRepository;
    private final VehicleTypeRepository vehicleTypeRepository;

    public PricePolicy createPricePolicy(CreatePricePolicyRequest request) {
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new RuntimeException("Vehicle type not found"));

        PricePolicy pricePolicy = new PricePolicy();
        pricePolicy.setPolicyName(request.getPolicyName());
        pricePolicy.setBasePrice(request.getBasePrice());
        pricePolicy.setBaseDurationMinutes(request.getBaseDurationMinutes());
        pricePolicy.setExtraHourPrice(request.getExtraHourPrice());
        pricePolicy.setActive(true);
        pricePolicy.setVehicleType(vehicleType);

        return pricePolicyRepository.save(pricePolicy);
    }

    public List<PricePolicy> getAllPricePolicies() {
        return pricePolicyRepository.findAll();
    }
}
