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
    public ParkingFloorResponse createParkingFloor(
            CreateParkingFloorRequest request
    ) {
        ParkingBranch parkingBranch =
                findBranch(request.getParkingBranchId());

        if (!parkingBranch.isActive()) {
            throw new ParkingSessionException("Parking branch is inactive");
        }

        boolean floorExists =parkingFloorRepository.existsByParkingBranchParkingBranchIdAndFloorNumber(parkingBranch.getParkingBranchId(),request.getFloorNumber());

        if (floorExists) {
            throw new ParkingSessionException("Floor number already exists in this branch");
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

    @Transactional(readOnly = true)
    public List<ParkingFloorResponse> getAllParkingFloors() {
        return parkingFloorRepository.findAll()
                .stream()
                .map(this::covertToParkingFloor)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ParkingFloorResponse> getFloorsByBranch(Long parkingBranchId) {
        findBranch(parkingBranchId);

        return parkingFloorRepository.findByParkingBranchParkingBranchIdOrderByFloorNumberAsc( parkingBranchId).stream().map(this::covertToParkingFloor).toList();
    }

    @Transactional(readOnly = true)
    public ParkingFloorResponse getParkingFloorById(Long id) {
        return covertToParkingFloor(findFloor(id));
    }

    // update parking floor
    @Transactional
    public ParkingFloorResponse updateParkingFloor(Long id, UpdateParkingFloorRequest request) {
        ParkingFloor parkingFloor = findFloor(id);
        
        ParkingBranch parkingBranch = parkingFloor.getParkingBranch();
        if(parkingBranch == null) {
            throw new ParkingSessionException("Parking floor does not belong to any branch");
        }
        if(!parkingBranch.isActive()) {
            throw new ParkingSessionException("Parking branch is inactive");
        }

        if (request.getParkingBranchId() != null && !request.getParkingBranchId().equals(parkingBranch.getParkingBranchId())) {
            ParkingBranch newBranch = findBranch(request.getParkingBranchId());
            if (!newBranch.isActive()) {
                throw new ParkingSessionException("New parking branch is inactive");
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
            throw new ParkingSessionException("Floor number already exists in this branch");
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

    @Transactional
    public ParkingFloorResponse updateStatus(Long id,boolean active) {
        ParkingFloor parkingFloor = findFloor(id);

        parkingFloor.setActive(active);

        return covertToParkingFloor(parkingFloorRepository.save(parkingFloor));
    }

    private ParkingBranch findBranch(Long id) {
        return parkingBranchRepository.findById(id)
                .orElseThrow(() ->new ParkingSessionException("Parking branch not found"));
    }

    private ParkingFloor findFloor(Long id) {
        return parkingFloorRepository.findById(id)
                .orElseThrow(() ->new ParkingSessionException("Parking floor not found"));
    }

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

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}