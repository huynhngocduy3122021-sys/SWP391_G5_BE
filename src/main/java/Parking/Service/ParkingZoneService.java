package Parking.Service;

import org.springframework.stereotype.Service;

import Parking.Model.ParkingZone;
import Parking.Model.VehicleType;
import Parking.Repository.ParkingZoneRepository;
import Parking.Repository.VehicleTypeRepository;
import Parking.dto.request.CreateParkingZoneRequest;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParkingZoneService {

    private final ParkingZoneRepository parkingZoneRepository;
    private final VehicleTypeRepository vehicleTypeRepository;

    public ParkingZone createParkingZone(CreateParkingZoneRequest request) {
        VehicleType vehicleType = vehicleTypeRepository.findById(request.getVehicleTypeId())
                .orElseThrow(() -> new RuntimeException("Vehicle type not found"));

        ParkingZone parkingZone = new ParkingZone();
        parkingZone.setZoneName(request.getZoneName());
        parkingZone.setMaxCapacity(request.getMaxCapacity());
        parkingZone.setCurrentCapacity(0);
        parkingZone.setActive(true);
        parkingZone.setVehicleType(vehicleType);

        return parkingZoneRepository.save(parkingZone);
    }

    public List<ParkingZone> getAllParkingZones() {
        return parkingZoneRepository.findAll();
    }
}
