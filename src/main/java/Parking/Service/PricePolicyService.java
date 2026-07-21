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

    // TẤN ANH TÚ NOTE: Tạo mới chính sách giá (giá cơ bản, giá phụ trội, block thời gian) cho loại phương tiện.
    public PricePolicy createPricePolicy(CreatePricePolicyRequest request) {
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại phương tiện"));

        PricePolicy pricePolicy = new PricePolicy();
        pricePolicy.setPolicyName(request.getPolicyName());
        pricePolicy.setBasePrice(request.getBasePrice());
        pricePolicy.setBaseDurationMinutes(request.getBaseDurationMinutes());
        pricePolicy.setExtraHourPrice(request.getExtraHourPrice());
        pricePolicy.setExtraDurationMinutes(request.getExtraDurationMinutes());
        pricePolicy.setActive(true);
        pricePolicy.setVehicleType(vehicleType);

        return pricePolicyRepository.save(pricePolicy);
    }

    public List<PricePolicy> getAllPricePolicies() {
        return pricePolicyRepository.findAll();
    }

    public PricePolicy getPricePolicyById(Long id) {
        return pricePolicyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chính sách giá có ID: " + id));
    }

    public PricePolicy updatePricePolicy(Long id, Parking.dto.request.UpdatePricePolicyRequest request) {
        PricePolicy pricePolicy = getPricePolicyById(id);
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại phương tiện có ID: " + request.getVehicleTypeId()));

        pricePolicy.setPolicyName(request.getPolicyName());
        pricePolicy.setBasePrice(request.getBasePrice());
        pricePolicy.setBaseDurationMinutes(request.getBaseDurationMinutes());
        pricePolicy.setExtraHourPrice(request.getExtraHourPrice());
        pricePolicy.setExtraDurationMinutes(request.getExtraDurationMinutes());
        pricePolicy.setActive(request.isActive());
        pricePolicy.setVehicleType(vehicleType);

        return pricePolicyRepository.save(pricePolicy);
    }

    public void deletePricePolicy(Long id) {
        PricePolicy pricePolicy = getPricePolicyById(id);
        pricePolicyRepository.delete(pricePolicy);
    }
}
