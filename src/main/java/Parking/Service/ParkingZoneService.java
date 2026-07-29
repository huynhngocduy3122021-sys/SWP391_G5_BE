package Parking.Service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Parking.Model.ParkingBranch;
import Parking.Model.ParkingFloor;
import Parking.Model.ParkingZone;
import Parking.Model.VehicleType;
import Parking.Repository.ParkingFloorRepository;
import Parking.Repository.ParkingZoneRepository;
import Parking.Repository.VehicleTypeRepository;
import Parking.dto.request.CreateParkingZoneRequest;
import Parking.dto.request.UpdateParkingZoneRequest;
import Parking.dto.response.ParkingZoneResponse;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParkingZoneService {

    private final ParkingZoneRepository parkingZoneRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final ParkingFloorRepository parkingFloorRepository;

    // Tạo phân khu đỗ xe liên kết 1-1 với tầng đỗ, cấu hình sức chứa (capacity) cho loại phương tiện
    @Transactional
    public ParkingZoneResponse createParkingZone(CreateParkingZoneRequest request) {
        ParkingFloor parkingFloor = findFloor(request.getParkingFloorId());

        VehicleType vehicleType =findVehicleType(request.getVehicleTypeId());

        if (!parkingFloor.isActive()) {
            throw new ParkingSessionException("Tầng đỗ xe đang ngừng hoạt động");
        }

        if (!parkingFloor.getParkingBranch().isActive()) {
            throw new ParkingSessionException("Chi nhánh bãi xe đang ngừng hoạt động");
        }

        if (parkingZoneRepository.existsByParkingFloorParkingFloorId(parkingFloor.getParkingFloorId())) {

            throw new ParkingSessionException("Tầng đỗ xe đã có khu vực đỗ xe");
        }

        ParkingZone parkingZone = new ParkingZone();

        parkingZone.setZoneName(
                request.getZoneName().trim()
        );
        parkingZone.setCapacity(
                request.getCapacity()
        );
        parkingZone.setActive(true);
        parkingZone.setParkingFloor(parkingFloor);
        parkingZone.setVehicleType(vehicleType);

        parkingFloor.setParkingZone(parkingZone);

        return convertToParkingZone(parkingZoneRepository.save(parkingZone));
    }

    // Lấy danh sách toàn bộ các khu vực đỗ xe trong hệ thống
    @Transactional(readOnly = true)
    public List<ParkingZoneResponse> getAllParkingZones() {
        return parkingZoneRepository.findAll()
                .stream()
                .map(this::convertToParkingZone)
                .toList();
    }

    // Lấy danh sách các khu vực đỗ xe thuộc một chi nhánh cụ thể
    @Transactional(readOnly = true)
    public List<ParkingZoneResponse> getZonesByBranch(
            Long parkingBranchId
    ) {
        return parkingZoneRepository.findByParkingFloorParkingBranchParkingBranchId(parkingBranchId)
                .stream()
                .map(this::convertToParkingZone)
                .toList();
    }

    // Lấy thông tin chi tiết khu vực đỗ xe theo ID
    @Transactional(readOnly = true)
    public ParkingZoneResponse getParkingZoneById(Long id) {
        return convertToParkingZone(findZone(id));
    }

    // Cập nhật thông tin khu vực đỗ xe (tên phân khu, sức chứa, tầng đỗ, loại phương tiện)
    @Transactional
    public ParkingZoneResponse updateParkingZone(Long id,UpdateParkingZoneRequest request) {
        ParkingZone parkingZone = findZone(id);

        ParkingFloor newFloor =findFloor(request.getParkingFloorId());

        VehicleType vehicleType =findVehicleType(request.getVehicleTypeId());

        boolean floorAlreadyHasOtherZone =parkingZoneRepository.existsByParkingFloorParkingFloorIdAndParkingZoneIdNot(newFloor.getParkingFloorId(),id);

        if (floorAlreadyHasOtherZone) {
            throw new ParkingSessionException("Tầng đỗ xe đã có một khu vực đỗ xe khác");
        }

        parkingZone.setZoneName(request.getZoneName().trim());
        parkingZone.setCapacity(request.getCapacity());
        parkingZone.setParkingFloor(newFloor);
        parkingZone.setVehicleType(vehicleType);

        newFloor.setParkingZone(parkingZone);

        return convertToParkingZone(parkingZoneRepository.save(parkingZone));
    }

    // Cập nhật trạng thái hoạt động (kích hoạt/khóa) của khu vực đỗ xe
    @Transactional
    public ParkingZoneResponse updateStatus(Long id,boolean active) {
        ParkingZone parkingZone = findZone(id);

        parkingZone.setActive(active);

        return convertToParkingZone(parkingZoneRepository.save(parkingZone));
    }

    // Tìm kiếm tầng đỗ xe theo ID, ném lỗi nếu không tìm thấy
    private ParkingFloor findFloor(Long id) {
        return parkingFloorRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy tầng đỗ xe"));
    }

    // Tìm kiếm loại phương tiện theo ID, ném lỗi nếu không tìm thấy
    private VehicleType findVehicleType(Long id) {
        return vehicleTypeRepository.findById(id)
                .orElseThrow(() ->new ParkingSessionException("Không tìm thấy loại phương tiện"));
    }

    // Tìm kiếm khu vực đỗ xe theo ID, ném lỗi nếu không tìm thấy
    private ParkingZone findZone(Long id) {
        return parkingZoneRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy khu vực đỗ xe"));
    }

    // Chuyển đổi đối tượng Entity ParkingZone sang DTO ParkingZoneResponse
    private ParkingZoneResponse convertToParkingZone(ParkingZone parkingZone) {
        ParkingFloor floor = parkingZone.getParkingFloor();

        ParkingBranch branch = floor.getParkingBranch();

        VehicleType vehicleType = parkingZone.getVehicleType();

        return ParkingZoneResponse.builder()
                .parkingZoneId(parkingZone.getParkingZoneId())
                .zoneName(parkingZone.getZoneName())
                .capacity(parkingZone.getCapacity())
                .active(parkingZone.isActive())
                .parkingFloorId(floor.getParkingFloorId())
                .parkingFloorName(floor.getFloorName())
                .parkingBranchId(branch.getParkingBranchId())
                .parkingBranchName(branch.getBranchName())
                .vehicleTypeId(vehicleType.getVehicleTypeId())
                .vehicleTypeName(vehicleType.getTypeName())
                .build();
    }
}
