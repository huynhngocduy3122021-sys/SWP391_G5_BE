package Parking.Service;

import java.util.List;
import java.util.Optional;

import Parking.Util.LicensePlateNormalizer;
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
    private final CurrentUserService currentUserService;

    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest request) {
        String licensePlate = LicensePlateNormalizer.normalize(request.getLicensePlate());
        User currentUser = currentUserService.getCurrentUser();

        VehicleType requestedType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy loại xe"));

        Optional<Vehicle> existingOpt = vehicleRepository.findByLicensePlateForUpdate(licensePlate);

        if (existingOpt.isPresent()) {
            Vehicle existing = existingOpt.get();

            if (existing.getUser() != null) {
                if (existing.getUser().getUserId().equals(currentUser.getUserId())) {
                    throw new ParkingSessionException("Biển số xe đã được đăng ký trong tài khoản của bạn");
                }
                throw new ParkingSessionException("Biển số xe đã được đăng ký bởi tài khoản khác");
            }

            if (existing.getVehicleSource() != VehicleSource.GUEST) {
                throw new ParkingSessionException("Biển số xe đã tồn tại và cần được nhân viên xác minh");
            }

            if (!existing.getVehicleType().getVehicleTypeId().equals(requestedType.getVehicleTypeId())) {
                throw new ParkingSessionException("Loại phương tiện không khớp với dữ liệu xe đã có");
            }

            existing.setUser(currentUser);
            existing.setVehicleSource(VehicleSource.REGISTER);
            existing.setDeleted(false);

            if (existing.getVehicleColor() == null) {
                existing.setVehicleColor(request.getVehicleColor());
            }
            if (existing.getVehicleBrand() == null) {
                existing.setVehicleBrand(request.getVehicleBrand());
            }

            return convertToResponse(vehicleRepository.save(existing));
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setLicensePlate(licensePlate);
        vehicle.setVehicleColor(request.getVehicleColor());
        vehicle.setVehicleBrand(request.getVehicleBrand());
        vehicle.setVehicleType(requestedType);
        vehicle.setUser(currentUser);
        vehicle.setVehicleSource(VehicleSource.REGISTER);

        return convertToResponse(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getAllVehicles() {
        return vehicleRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(Long id) {
        return convertToResponse(findVehicle(id));
    }

    @Transactional
    public VehicleResponse updateVehicle(Long id, UpdateVehicleRequest request) {
        Vehicle vehicle = findVehicle(id);

        if (request.getLicensePlate() != null && !request.getLicensePlate().isBlank()) {
            String cleaned = LicensePlateNormalizer.normalize(request.getLicensePlate());
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

    @Transactional
    public VehicleResponse deleteVehicle(Long id) {
        Vehicle vehicle = findVehicle(id);
        vehicle.setDeleted(!vehicle.isDeleted());
        return convertToResponse(vehicleRepository.save(vehicle));
    }

    private Vehicle findVehicle(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy xe"));
    }

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
