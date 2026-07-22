package Parking.Service;

import org.springframework.stereotype.Service;

import Parking.Model.VehicleType;
import Parking.Repository.VehicleTypeRepository;
import Parking.dto.request.CreateVehicleTypeRequest;
import lombok.RequiredArgsConstructor;
import java.util.List;
@Service
@RequiredArgsConstructor
public class VehicleTypeService {
    private final VehicleTypeRepository vehicleTypeRepository;

    // Tạo mới loại phương tiện phục vụ cấu hình biểu phí, phân khu và kiểm tra quyền đặt chỗ
    public VehicleType createVehicleType(CreateVehicleTypeRequest request) {
        VehicleType vehicleType = new VehicleType();
        vehicleType.setTypeName(request.getTypeName());
        vehicleType.setDescription(request.getDescription());

        return vehicleTypeRepository.save(vehicleType);
    }

    // Lấy danh sách toàn bộ các loại phương tiện trong hệ thống
    public List<VehicleType> getAllVehicleTypes() {
        return vehicleTypeRepository.findAll();
    }

    // Lấy thông tin chi tiết loại phương tiện theo ID
    public VehicleType getVehicleTypeById(Long id) {
        return vehicleTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại phương tiện có ID: " + id));
    }

    // Cập nhật tên và mô tả của loại phương tiện
    public VehicleType updateVehicleType(Long id, Parking.dto.request.UpdateVehicleTypeRequest request) {
        VehicleType vehicleType = getVehicleTypeById(id);
        vehicleType.setTypeName(request.getTypeName());
        vehicleType.setDescription(request.getDescription());
        return vehicleTypeRepository.save(vehicleType);
    }

    // Xóa loại phương tiện theo ID
    public void deleteVehicleType(Long id) {
        VehicleType vehicleType = getVehicleTypeById(id);
        vehicleTypeRepository.delete(vehicleType);
    }
}
