package Parking.Service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Parking.Model.User;
import Parking.Model.Vehicle;
import Parking.Model.VehicleType;
import Parking.Repository.UserRepository;
import Parking.Repository.VehicleRepository;
import Parking.Repository.VehicleTypeRepository;
import Parking.dto.request.CreateVehicleRequest;
import Parking.dto.request.UpdateVehicleRequest;
import Parking.dto.response.VehicleResponse;
import Parking.enums.VehicleSource;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final UserRepository userRepository;

    // Đăng ký xe mới, chuẩn hóa biển số, gán chủ sở hữu (User) hoặc để trống đối với xe vãng lai (GUEST)
    public VehicleResponse createVehicle(CreateVehicleRequest request) {
        // Chuẩn hóa biển số xe bằng cách xóa khoảng trắng thừa ở hai đầu
        String licensePlate = request.getLicensePlate().trim();
        // Kiểm tra xem biển số xe đã tồn tại trong hệ thống chưa
        if (vehicleRepository.existsByLicensePlateIgnoreCase(licensePlate)) {
            throw new ParkingSessionException("Biển số xe đã tồn tại");
        }

        // Tìm thông tin loại xe theo ID được gửi lên
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy loại xe"));

        // Tạo đối tượng thực thể xe mới và gán các thông tin cơ bản
        Vehicle vehicle = new Vehicle();
        vehicle.setLicensePlate(licensePlate);
        vehicle.setVehicleColor(request.getVehicleColor());
        vehicle.setVehicleBrand(request.getVehicleBrand());
        vehicle.setVehicleType(vehicleType);
        // Nếu có ID người dùng đi kèm, thực hiện tìm kiếm và liên kết chủ sở hữu cho xe
        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy người dùng"));
            vehicle.setUser(user);
        }

        // Lưu thông tin xe vào cơ sở dữ liệu và trả về kết quả dạng response DTO
        return convertToResponse(vehicleRepository.save(vehicle));
    }

    // Lấy danh sách toàn bộ xe trong hệ thống
    @Transactional(readOnly = true)
    public List<VehicleResponse> getAllVehicles() {
        return vehicleRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Lấy thông tin chi tiết của xe theo ID
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(Long id) {
        return convertToResponse(findVehicle(id));
    }

    // Cập nhật thông tin xe (biển số, màu sắc, hãng xe, loại xe, chủ sở hữu)
    @Transactional
    public VehicleResponse updateVehicle(Long id, UpdateVehicleRequest request) {
        // Tìm thông tin xe hiện tại trong hệ thống theo ID
        Vehicle vehicle = findVehicle(id);

        // Cập nhật biển số xe nếu có thay đổi và kiểm tra trùng lặp biển số mới
        if (request.getLicensePlate() != null && !request.getLicensePlate().isBlank()) {
            String cleaned = request.getLicensePlate().trim();
            if (!vehicle.getLicensePlate().equalsIgnoreCase(cleaned) && vehicleRepository.existsByLicensePlateIgnoreCase(cleaned)) {
                throw new ParkingSessionException("Biển số xe đã tồn tại");
            }
            vehicle.setLicensePlate(cleaned);
        }

        // Cập nhật màu sắc xe nếu được cung cấp
        if (request.getVehicleColor() != null) {
            vehicle.setVehicleColor(request.getVehicleColor());
        }
        // Cập nhật thương hiệu xe nếu được cung cấp
        if (request.getVehicleBrand() != null) {
            vehicle.setVehicleBrand(request.getVehicleBrand());
        }
        // Cập nhật loại xe mới nếu được cung cấp
        if (request.getVehicleTypeId() != null) {
            VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy loại xe"));
            vehicle.setVehicleType(vehicleType);
        }
        // Cập nhật thông tin chủ xe mới nếu được cung cấp
        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy người dùng"));
            vehicle.setUser(user);
        }

        // Lưu thông tin cập nhật vào CSDL và trả về kết quả dạng response DTO
        return convertToResponse(vehicleRepository.save(vehicle));
    }

    // Thực hiện xóa mềm (Soft Delete) lật cờ deleted để bảo toàn tính nhất quán dữ liệu khóa ngoại
    @Transactional
    public VehicleResponse deleteVehicle(Long id) {
        // Tìm thông tin xe cần xóa theo ID
        Vehicle vehicle = findVehicle(id);
        // Đảo ngược trạng thái cờ xóa (kích hoạt/hủy kích hoạt xe)
        vehicle.setDeleted(!vehicle.isDeleted());
        // Lưu thay đổi vào CSDL và trả về kết quả
        return convertToResponse(vehicleRepository.save(vehicle));
    }

    // Tìm thông tin xe theo ID, ném lỗi nếu không tìm thấy
    private Vehicle findVehicle(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy xe"));
    }

    // Chuyển đổi đối tượng Entity Vehicle sang DTO VehicleResponse
    private VehicleResponse convertToResponse(Vehicle vehicle) {
        User owner = vehicle.getUser();
        VehicleType type = vehicle.getVehicleType();

        return VehicleResponse.builder()
                .vehicleId(vehicle.getVehiclesId())
                .licensePlate(vehicle.getLicensePlate())
                .vehicleColor(vehicle.getVehicleColor())
                .vehicleBrand(vehicle.getVehicleBrand())
                .vehicleSource(owner != null ? VehicleSource.REGISTER : vehicle.getVehicleSource())
                .userId(owner != null ? owner.getUserId() : null)
                .userFullName(owner != null ? owner.getUserFullName() : null)
                .vehicleTypeId(type != null ? type.getVehicleTypeId() : null)
                .vehicleTypeName(type != null ? type.getTypeName() : null)
                .deleted(vehicle.isDeleted())
                .build();
    }
}
