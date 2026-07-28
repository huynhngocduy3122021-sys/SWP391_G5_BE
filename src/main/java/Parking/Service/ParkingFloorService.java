package Parking.Service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Parking.Model.ParkingBranch;
import Parking.Model.ParkingFloor;
import Parking.Repository.ParkingBranchRepository;
import Parking.Repository.ParkingFloorRepository;
import Parking.dto.request.CreateParkingFloorRequest;
import Parking.dto.request.UpdateParkingFloorRequest;
import Parking.dto.response.ParkingFloorResponse;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParkingFloorService {

    private final ParkingFloorRepository parkingFloorRepository;
    private final ParkingBranchRepository parkingBranchRepository;

    //create parkingFLoor
    @Transactional
    // Tạo tầng mới, kiểm tra chi nhánh hoạt động và số tầng không được trùng lặp
    public ParkingFloorResponse createParkingFloor(
            CreateParkingFloorRequest request
    ) {
        ParkingBranch parkingBranch =
                findBranch(request.getParkingBranchId());

        if (!parkingBranch.isActive()) {
            throw new ParkingSessionException("Chi nhánh bãi xe đang ngừng hoạt động");
        }

        boolean floorExists =parkingFloorRepository.existsByParkingBranchParkingBranchIdAndFloorNumber(parkingBranch.getParkingBranchId(),request.getFloorNumber());

        if (floorExists) {
            throw new ParkingSessionException("Số tầng đã tồn tại trong chi nhánh này");
        }

        ParkingFloor parkingFloor = new ParkingFloor();

        parkingFloor.setFloorName(request.getFloorName().trim());
        parkingFloor.setFloorNumber( request.getFloorNumber());
        parkingFloor.setDescription( normalizeOptional(request.getDescription()));
        parkingFloor.setActive(true);
        parkingFloor.setParkingBranch(parkingBranch);

        return covertToParkingFloor(
                parkingFloorRepository.save(parkingFloor)
        );
    }

    // Lấy danh sách tất cả các tầng đỗ xe trong hệ thống
    @Transactional(readOnly = true)
    public List<ParkingFloorResponse> getAllParkingFloors() {
        return parkingFloorRepository.findAll()
                .stream()
                .map(this::covertToParkingFloor)
                .toList();
    }

    // Lấy danh sách các tầng đỗ xe thuộc một chi nhánh cụ thể, sắp xếp theo số tầng tăng dần
    @Transactional(readOnly = true)
    public List<ParkingFloorResponse> getFloorsByBranch(Long parkingBranchId) {
        findBranch(parkingBranchId);

        return parkingFloorRepository.findByParkingBranchParkingBranchIdOrderByFloorNumberAsc( parkingBranchId).stream().map(this::covertToParkingFloor).toList();
    }

    // Lấy thông tin chi tiết của tầng đỗ xe theo ID
    @Transactional(readOnly = true)
    public ParkingFloorResponse getParkingFloorById(Long id) {
        return covertToParkingFloor(findFloor(id));
    }

    // Cập nhật thông tin tầng đỗ xe (tên, số tầng, mô tả, chi nhánh đỗ xe)
    @Transactional
    public ParkingFloorResponse updateParkingFloor(Long id, UpdateParkingFloorRequest request) {
        ParkingFloor parkingFloor = findFloor(id);
        
        ParkingBranch parkingBranch = parkingFloor.getParkingBranch();
        if(parkingBranch == null) {
            throw new ParkingSessionException("Tầng đỗ xe không thuộc chi nhánh nào");
        }
        if(!parkingBranch.isActive()) {
            throw new ParkingSessionException("Chi nhánh bãi xe đang ngừng hoạt động");
        }

        if (request.getParkingBranchId() != null && !request.getParkingBranchId().equals(parkingBranch.getParkingBranchId())) {
            ParkingBranch newBranch = findBranch(request.getParkingBranchId());
            if (!newBranch.isActive()) {
                throw new ParkingSessionException("Chi nhánh bãi xe mới đang ngừng hoạt động");
            }
            parkingFloor.setParkingBranch(newBranch);
            parkingBranch = newBranch;
        }

        Integer floorNumber = request.getFloorNumber() != null ? request.getFloorNumber() : parkingFloor.getFloorNumber();
        boolean duplicateFloor = parkingFloorRepository.existsByParkingBranchParkingBranchIdAndFloorNumberAndParkingFloorIdNot(
                parkingBranch.getParkingBranchId(),
                floorNumber,
                id
        );

        if (duplicateFloor) {
            throw new ParkingSessionException("Số tầng đã tồn tại trong chi nhánh này");
        }
        if(request.getFloorName() != null && !request.getFloorName().isBlank()) {
            parkingFloor.setFloorName(request.getFloorName().trim());
        }

        if(request.getFloorNumber() != null) {
            parkingFloor.setFloorNumber(request.getFloorNumber());
        }
        if(request.getDescription() != null) {
            parkingFloor.setDescription(normalizeOptional(request.getDescription()));
        }
        

        return covertToParkingFloor(parkingFloorRepository.save(parkingFloor));
    }

    // Cập nhật trạng thái hoạt động (kích hoạt/khóa) của tầng đỗ xe
    @Transactional
    public ParkingFloorResponse updateStatus(Long id,boolean active) {
        ParkingFloor parkingFloor = findFloor(id);

        parkingFloor.setActive(active);

        return covertToParkingFloor(parkingFloorRepository.save(parkingFloor));
    }

    // Tìm kiếm chi nhánh bãi xe theo ID, ném lỗi nếu không tìm thấy
    private ParkingBranch findBranch(Long id) {
        return parkingBranchRepository.findById(id)
                .orElseThrow(() ->new ParkingSessionException("Không tìm thấy chi nhánh bãi xe"));
    }

    // Tìm kiếm tầng đỗ xe theo ID, ném lỗi nếu không tìm thấy
    private ParkingFloor findFloor(Long id) {
        return parkingFloorRepository.findById(id)
                .orElseThrow(() ->new ParkingSessionException("Không tìm thấy tầng đỗ xe"));
    }

    // Chuyển đổi đối tượng Entity ParkingFloor sang DTO ParkingFloorResponse
    private ParkingFloorResponse covertToParkingFloor(ParkingFloor parkingFloor) {
        ParkingBranch branch =parkingFloor.getParkingBranch();

        return ParkingFloorResponse.builder()
                .parkingFloorId(parkingFloor.getParkingFloorId())
                .floorName(parkingFloor.getFloorName())
                .floorNumber(parkingFloor.getFloorNumber())
                .description(parkingFloor.getDescription())
                .active(parkingFloor.isActive())
                .parkingBranchId(branch.getParkingBranchId())
                .parkingBranchName(branch.getBranchName())
                .build();
    }

    // Chuẩn hóa dữ liệu văn bản tùy chọn (loại bỏ khoảng trắng thừa, chuyển thành null nếu rỗng)
    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
