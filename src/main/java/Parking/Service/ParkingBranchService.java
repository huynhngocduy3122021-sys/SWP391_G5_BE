package Parking.Service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Parking.Model.ParkingBranch;
import Parking.Repository.ParkingBranchRepository;
import Parking.dto.request.CreateParkingBranchRequest;
import Parking.dto.request.UpdateParkingBranchRequest;
import Parking.dto.response.ParkingBranchResponse;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParkingBranchService {

    private final ParkingBranchRepository parkingBranchRepository;

    @Transactional
    // ham tao branch
    public ParkingBranchResponse createParkingBranch(CreateParkingBranchRequest request) {
        String branchName = request.getBranchName().trim();

        if (parkingBranchRepository.existsByBranchNameIgnoreCase(branchName)) {
            throw new ParkingSessionException("Parking branch name already exists" );
        }

        ParkingBranch parkingBranch = new ParkingBranch();

        parkingBranch.setBranchName(branchName);
        parkingBranch.setAddress(request.getAddress().trim());
        parkingBranch.setPhoneNumber(normalizeOptional(request.getPhoneNumber()));
        parkingBranch.setDescription(normalizeOptional(request.getDescription()));
        parkingBranch.setActive(true);

        return convertBranchResponse(parkingBranchRepository.save(parkingBranch));
    }
    // ham lay tat ca
    @Transactional(readOnly = true)
    public List<ParkingBranchResponse> getAllParkingBranches() {
        return parkingBranchRepository.findAll()
                .stream()
                .map(this::convertBranchResponse)
                .toList();
    }
   @Transactional(readOnly = true)
   // ham lay theo id
    public ParkingBranchResponse getParkingBranchById(Long id) {
        return convertBranchResponse(findBranch(id));
    }

    @Transactional
    public ParkingBranchResponse updateParkingBranch(Long id,UpdateParkingBranchRequest request) {
        ParkingBranch parkingBranch = findBranch(id);

        if(request.getBranchName() != null && !request.getBranchName().isBlank()) {
            String newName = request.getBranchName().trim();
            if (!parkingBranch.getBranchName().equalsIgnoreCase(newName) && parkingBranchRepository.existsByBranchNameIgnoreCase(newName)) {
                throw new ParkingSessionException("Parking branch name already exists");
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

    @Transactional
    public ParkingBranchResponse updateStatus(Long id,boolean active) {
        ParkingBranch parkingBranch = findBranch(id);
        parkingBranch.setActive(active);
        return convertBranchResponse(parkingBranchRepository.save(parkingBranch));
    }

    private ParkingBranch findBranch(Long id) {
        return parkingBranchRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Parking branch not found"));
    }

    private ParkingBranchResponse convertBranchResponse(ParkingBranch parkingBranch) {
        return ParkingBranchResponse.builder()
                .parkingBranchId(parkingBranch.getParkingBranchId())
                .branchName(parkingBranch.getBranchName())
                .address(parkingBranch.getAddress())
                .phoneNumber(parkingBranch.getPhoneNumber())
                .description(parkingBranch.getDescription())
                .active(parkingBranch.isActive())
                .build();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}