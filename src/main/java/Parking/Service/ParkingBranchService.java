package Parking.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Parking.Model.ParkingBranch;
import Parking.Repository.BookingRepository;
import Parking.Repository.ParkingBranchRepository;
import Parking.Repository.ParkingZoneRepository;
import Parking.Repository.ParkingSessionRepository;
import Parking.enums.ParkingSessionStatus;
import Parking.dto.request.CreateParkingBranchRequest;
import Parking.dto.request.UpdateParkingBranchRequest;
import Parking.dto.response.ParkingBranchResponse;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParkingBranchService {

    private final ParkingBranchRepository parkingBranchRepository;
    private final ParkingZoneRepository parkingZoneRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final BookingRepository bookingRepository;

    // Tạo chi nhánh bãi xe mới với tên chi nhánh độc nhất (case-insensitive) và kích hoạt trạng thái mặc định
    @Transactional
    public ParkingBranchResponse createParkingBranch(CreateParkingBranchRequest request) {
        String branchName = request.getBranchName().trim();

        if (parkingBranchRepository.existsByBranchNameIgnoreCase(branchName)) {
            throw new ParkingSessionException("Tên chi nhánh bãi xe đã tồn tại");
        }

        ParkingBranch parkingBranch = new ParkingBranch();

        parkingBranch.setBranchName(branchName);
        parkingBranch.setAddress(request.getAddress().trim());
        parkingBranch.setPhoneNumber(normalizeOptional(request.getPhoneNumber()));
        parkingBranch.setDescription(normalizeOptional(request.getDescription()));
        parkingBranch.setActive(true);

        return convertBranchResponse(parkingBranchRepository.save(parkingBranch));
    }

    // Lấy danh sách tất cả các chi nhánh bãi xe trong hệ thống
    @Transactional(readOnly = true)
    public List<ParkingBranchResponse> getAllParkingBranches() {
        return parkingBranchRepository.findAll()
                .stream()
                .map(this::convertBranchResponse)
                .toList();
    }

    // Lấy thông tin chi tiết chi nhánh bãi xe theo ID
    @Transactional(readOnly = true)
    public ParkingBranchResponse getParkingBranchById(Long id) {
        return convertBranchResponse(findBranch(id));
    }

    // Cập nhật thông tin chi nhánh bãi xe (tên, địa chỉ, số điện thoại, mô tả)
    @Transactional
    public ParkingBranchResponse updateParkingBranch(Long id,UpdateParkingBranchRequest request) {
        ParkingBranch parkingBranch = findBranch(id);

        if(request.getBranchName() != null && !request.getBranchName().isBlank()) {
            String newName = request.getBranchName().trim();
            if (!parkingBranch.getBranchName().equalsIgnoreCase(newName) && parkingBranchRepository.existsByBranchNameIgnoreCase(newName)) {
                throw new ParkingSessionException("Tên chi nhánh bãi xe đã tồn tại");
            }
            parkingBranch.setBranchName(newName);
        }
        if(request.getAddress() != null && !request.getAddress().isBlank()) {
            parkingBranch.setAddress(request.getAddress().trim());
        }
        if(request.getPhoneNumber() != null) {
            parkingBranch.setPhoneNumber(normalizeOptional(request.getPhoneNumber()));
        }
        if(request.getDescription() != null) {
            parkingBranch.setDescription(normalizeOptional(request.getDescription()));
        }

        return convertBranchResponse(parkingBranchRepository.save(parkingBranch));
    }

    // Cập nhật trạng thái hoạt động (kích hoạt/khóa) của chi nhánh bãi xe
    @Transactional
    public ParkingBranchResponse updateStatus(Long id,boolean active) {
        ParkingBranch parkingBranch = findBranch(id);
        parkingBranch.setActive(active);
        return convertBranchResponse(parkingBranchRepository.save(parkingBranch));
    }

    // Tìm kiếm chi nhánh bãi xe theo ID, ném lỗi nếu không tìm thấy
    private ParkingBranch findBranch(Long id) {
        return parkingBranchRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy chi nhánh bãi xe"));
    }

    // Chuyển đổi đối tượng Entity ParkingBranch sang DTO ParkingBranchResponse, tính toán sức chứa thực tế
    private ParkingBranchResponse convertBranchResponse(ParkingBranch parkingBranch) {
        Long branchId = parkingBranch.getParkingBranchId();
        Long totalCapacityLong = parkingZoneRepository.calculateTotalCapacityByBranch(branchId);
        int totalCapacity = totalCapacityLong != null ? totalCapacityLong.intValue() : 0;

        long activeSessionsLong = parkingSessionRepository.countByParkingBranchParkingBranchIdAndStatus(branchId, ParkingSessionStatus.ACTIVE);
        int activeSessions = (int) activeSessionsLong;

        long activeBookingsLong = bookingRepository.countActiveBookingsByBranch(branchId, LocalDateTime.now());
        int activeBookings = (int) activeBookingsLong;

        int availableCapacity = Math.max(0, totalCapacity - activeSessions - activeBookings);

        return ParkingBranchResponse.builder()
                .parkingBranchId(branchId)
                .branchName(parkingBranch.getBranchName())
                .address(parkingBranch.getAddress())
                .phoneNumber(parkingBranch.getPhoneNumber())
                .description(parkingBranch.getDescription())
                .active(parkingBranch.isActive())
                .totalCapacity(totalCapacity)
                .availableCapacity(availableCapacity)
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
