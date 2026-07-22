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
        String licensePlate = request.getLicensePlate().trim();
        if (vehicleRepository.existsByLicensePlateIgnoreCase(licensePlate)) {
            throw new ParkingSessionException("Biển số xe đã tồn tại");
        }

        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy loại xe"));

        Vehicle vehicle = new Vehicle();
        vehicle.setLicensePlate(licensePlate);
        vehicle.setVehicleColor(request.getVehicleColor());
        vehicle.setVehicleBrand(request.getVehicleBrand());
        vehicle.setVehicleType(vehicleType);
        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy người dùng"));
            vehicle.setUser(user);
        }

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
        Vehicle vehicle = findVehicle(id);

        if (request.getLicensePlate() != null && !request.getLicensePlate().isBlank()) {
            String cleaned = request.getLicensePlate().trim();
            if (!vehicle.getLicensePlate().equalsIgnoreCase(cleaned) && vehicleRepository.existsByLicensePlateIgnoreCase(cleaned)) {
                throw new ParkingSessionException("Biển số xe đã tồn tại");
            }
            vehicle.setLicensePlate(cleaned);
        }

        if (request.getVehicleColor() != null) {
            vehicle.setVehicleColor(request.getVehicleColor());
        }
        if (request.getVehicleBrand() != null) {
            vehicle.setVehicleBrand(request.getVehicleBrand());
        }
        if (request.getVehicleTypeId() != null) {
            VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy loại xe"));
            vehicle.setVehicleType(vehicleType);
        }
        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy người dùng"));
            vehicle.setUser(user);
        }

        return convertToResponse(vehicleRepository.save(vehicle));
    }

    // Thực hiện xóa mềm (Soft Delete) lật cờ deleted để bảo toàn tính nhất quán dữ liệu khóa ngoại
    @Transactional
    public VehicleResponse deleteVehicle(Long id) {
        Vehicle vehicle = findVehicle(id);
        vehicle.setDeleted(!vehicle.isDeleted());
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
