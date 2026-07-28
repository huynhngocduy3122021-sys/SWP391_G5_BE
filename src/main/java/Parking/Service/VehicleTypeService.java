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
        // Khởi tạo thực thể loại phương tiện mới
        VehicleType vehicleType = new VehicleType();
        // Thiết lập tên loại phương tiện (ví dụ: Xe máy, Ô tô)
        vehicleType.setTypeName(request.getTypeName());
        // Thiết lập mô tả chi tiết cho loại phương tiện
        vehicleType.setDescription(request.getDescription());

        // Lưu thông tin loại phương tiện mới vào CSDL
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
        // Tìm thông tin loại phương tiện cần cập nhật theo ID
        VehicleType vehicleType = getVehicleTypeById(id);
        // Thiết lập tên mới từ request
        vehicleType.setTypeName(request.getTypeName());
        // Thiết lập mô tả mới từ request
        vehicleType.setDescription(request.getDescription());
        // Lưu thay đổi vào CSDL
        return vehicleTypeRepository.save(vehicleType);
    }

    // Xóa loại phương tiện theo ID
    public void deleteVehicleType(Long id) {
        // Tìm thông tin loại phương tiện cần xóa theo ID
        VehicleType vehicleType = getVehicleTypeById(id);
        // Xóa loại phương tiện ra khỏi CSDL
        vehicleTypeRepository.delete(vehicleType);
    }
}
