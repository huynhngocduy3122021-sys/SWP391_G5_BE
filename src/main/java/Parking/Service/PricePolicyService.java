package Parking.Service;

import org.springframework.stereotype.Service;

import Parking.Model.PricePolicy;
import Parking.Model.VehicleType;
import Parking.Repository.PricePolicyRepository;
import Parking.Repository.VehicleTypeRepository;
import Parking.dto.request.CreatePricePolicyRequest;
import Parking.dto.request.UpdatePricePolicyRequest;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PricePolicyService {
     private final PricePolicyRepository pricePolicyRepository;
    private final VehicleTypeRepository vehicleTypeRepository;

    // Tạo mới chính sách giá (giá cơ bản, giá phụ trội, block thời gian) cho loại phương tiện
    @Transactional
    public PricePolicy createPricePolicy(CreatePricePolicyRequest request) {
        // Tìm thông tin loại phương tiện được áp dụng chính sách giá, ném lỗi nếu không tìm thấy
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại phương tiện"));

        validateSinglePolicyPerCategory(request.getPolicyName(), request.getVehicleTypeId(), null);

        // Khởi tạo thực thể chính sách giá mới
        PricePolicy pricePolicy = new PricePolicy();
        // Thiết lập tên chính sách giá
        pricePolicy.setPolicyName(request.getPolicyName());
        // Thiết lập giá cơ bản (giá mở cửa/block đầu tiên)
        pricePolicy.setBasePrice(request.getBasePrice());
        // Thiết lập thời gian cơ bản (đơn vị: phút) cho block đầu tiên
        pricePolicy.setBaseDurationMinutes(request.getBaseDurationMinutes());
        // Thiết lập giá phụ trội cho mỗi block tiếp theo (ví dụ: mỗi giờ tiếp theo)
        pricePolicy.setExtraHourPrice(request.getExtraHourPrice());
        // Thiết lập độ dài thời gian block phụ trội (đơn vị: phút)
        pricePolicy.setExtraDurationMinutes(request.getExtraDurationMinutes());
        // Kích hoạt hoạt động mặc định cho chính sách mới
        pricePolicy.setActive(true);
        // Gán loại phương tiện áp dụng cho chính sách này
        pricePolicy.setVehicleType(vehicleType);

        // Lưu chính sách giá mới vào CSDL
        return pricePolicyRepository.save(pricePolicy);
    }

    // Lấy danh sách toàn bộ các chính sách giá trong hệ thống
    public List<PricePolicy> getAllPricePolicies() {
        return pricePolicyRepository.findAll();
    }

    // Lấy thông tin chi tiết chính sách giá theo ID
    public PricePolicy getPricePolicyById(Long id) {
        return pricePolicyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chính sách giá có ID: " + id));
    }

    // Cập nhật thông tin chính sách giá và gán loại phương tiện áp dụng
    @Transactional
    public PricePolicy updatePricePolicy(Long id, UpdatePricePolicyRequest request) {
        // Tìm thông tin chính sách giá hiện tại trong hệ thống theo ID
        PricePolicy pricePolicy = getPricePolicyById(id);
        // Tìm loại phương tiện mới áp dụng từ request
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại phương tiện có ID: " + request.getVehicleTypeId()));

        validateSinglePolicyPerCategory(request.getPolicyName(), request.getVehicleTypeId(), id);

        // Cập nhật lại các thông số giá và thời gian của chính sách
        pricePolicy.setPolicyName(request.getPolicyName());
        pricePolicy.setBasePrice(request.getBasePrice());
        pricePolicy.setBaseDurationMinutes(request.getBaseDurationMinutes());
        pricePolicy.setExtraHourPrice(request.getExtraHourPrice());
        pricePolicy.setExtraDurationMinutes(request.getExtraDurationMinutes());
        pricePolicy.setActive(request.isActive());
        pricePolicy.setVehicleType(vehicleType);

        // Lưu các thông tin cập nhật vào CSDL
        return pricePolicyRepository.save(pricePolicy);
    }

    // Xóa chính sách giá theo ID
    public void deletePricePolicy(Long id) {
        // Tìm chính sách giá cần xóa theo ID
        PricePolicy pricePolicy = getPricePolicyById(id);
        // Xóa chính sách giá ra khỏi CSDL
        pricePolicyRepository.delete(pricePolicy);
    }

    private void validateSinglePolicyPerCategory(String policyName, Long vehicleTypeId, Long currentPolicyId) {
        boolean packagePolicy = isPackagePolicy(policyName);
        List<PricePolicy> policiesInSameCategory = packagePolicy
                ? pricePolicyRepository.findPackagePolicies(vehicleTypeId)
                : pricePolicyRepository.findHourlyPolicies(vehicleTypeId);

        boolean duplicateExists = policiesInSameCategory.stream()
                .anyMatch(policy -> currentPolicyId == null
                        || !policy.getPricePolicyId().equals(currentPolicyId));

        if (duplicateExists) {
            String policyCategory = packagePolicy ? "gói đăng ký" : "chính sách giá check-in/check-out";
            throw new ParkingSessionException("Loại phương tiện này đã có " + policyCategory
                    + ". Vui lòng chỉnh sửa chính sách hiện có.");
        }
    }

    private boolean isPackagePolicy(String policyName) {
        if (policyName == null) {
            return false;
        }

        String normalizedName = policyName.trim().toLowerCase();
        return normalizedName.contains("gói") || normalizedName.contains("tháng");
    }
}
