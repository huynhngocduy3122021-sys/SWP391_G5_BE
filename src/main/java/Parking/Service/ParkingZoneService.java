package Parking.Service;

import java.util.List;

import org.springframework.http.HttpStatus;
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

    @Transactional
    public ParkingZoneResponse createParkingZone(CreateParkingZoneRequest request) {
        ParkingFloor parkingFloor = findFloor(request.getParkingFloorId());

        VehicleType vehicleType =findVehicleType(request.getVehicleTypeId());

        if (!parkingFloor.isActive()) {
            throw new ParkingSessionException("Parking floor is inactive");
        }

        if (!parkingFloor.getParkingBranch().isActive()) {
            throw new ParkingSessionException("Parking branch is inactive");
        }

        if (parkingZoneRepository.existsByParkingFloorParkingFloorId(parkingFloor.getParkingFloorId())) {

            throw new ParkingSessionException("Parking floor already has a parking zone");
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

    @Transactional(readOnly = true)
    public List<ParkingZoneResponse> getAllParkingZones() {
        return parkingZoneRepository.findAll()
                .stream()
                .map(this::convertToParkingZone)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ParkingZoneResponse> getZonesByBranch(
            Long parkingBranchId
    ) {
        return parkingZoneRepository.findByParkingFloorParkingBranchParkingBranchId(parkingBranchId)
                .stream()
                .map(this::convertToParkingZone)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkingZoneResponse getParkingZoneById(Long id) {
        return convertToParkingZone(findZone(id));
    }

    @Transactional
    public ParkingZoneResponse updateParkingZone(Long id,UpdateParkingZoneRequest request) {
        ParkingZone parkingZone = findZone(id);

        ParkingFloor newFloor =findFloor(request.getParkingFloorId());

        VehicleType vehicleType =findVehicleType(request.getVehicleTypeId());

        boolean floorAlreadyHasOtherZone =parkingZoneRepository.existsByParkingFloorParkingFloorIdAndParkingZoneIdNot(newFloor.getParkingFloorId(),id);

        if (floorAlreadyHasOtherZone) {
            throw new ParkingSessionException("Parking floor already has another parking zone");
        }

        parkingZone.setZoneName(request.getZoneName().trim());
        parkingZone.setCapacity(request.getCapacity());
        parkingZone.setParkingFloor(newFloor);
        parkingZone.setVehicleType(vehicleType);

        newFloor.setParkingZone(parkingZone);

        return convertToParkingZone(parkingZoneRepository.save(parkingZone));
    }

    @Transactional
    public ParkingZoneResponse updateStatus(Long id,boolean active) {
        ParkingZone parkingZone = findZone(id);

        parkingZone.setActive(active);

        return convertToParkingZone(parkingZoneRepository.save(parkingZone));
    }

    private ParkingFloor findFloor(Long id) {
        return parkingFloorRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Parking floor not found"));
    }

    private VehicleType findVehicleType(Long id) {
        return vehicleTypeRepository.findById(id)
                .orElseThrow(() ->new ParkingSessionException("Vehicle type not found"));
    }

    private ParkingZone findZone(Long id) {
        return parkingZoneRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Parking zone not found"));
    }

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