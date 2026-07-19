package Parking.Service;

import org.springframework.stereotype.Service;

import Parking.Model.VehicleType;
import Parking.Repository.VehicleTypeRepository;
import Parking.dto.request.CreateVehicleTypeRequest;
import lombok.RequiredArgsConstructor;
import java.util.List;
@Service
@RequiredArgsConstructor
public class VehicleTypeService {
    private final VehicleTypeRepository vehicleTypeRepository;

    public VehicleType createVehicleType(CreateVehicleTypeRequest request) {
        VehicleType vehicleType = new VehicleType();
        vehicleType.setTypeName(request.getTypeName());
        vehicleType.setDescription(request.getDescription());

        return vehicleTypeRepository.save(vehicleType);
    }

    public List<VehicleType> getAllVehicleTypes() {
        return vehicleTypeRepository.findAll();
    }

    public VehicleType getVehicleTypeById(Long id) {
        return vehicleTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại phương tiện có ID: " + id));
    }

    public VehicleType updateVehicleType(Long id, Parking.dto.request.UpdateVehicleTypeRequest request) {
        VehicleType vehicleType = getVehicleTypeById(id);
        vehicleType.setTypeName(request.getTypeName());
        vehicleType.setDescription(request.getDescription());
        return vehicleTypeRepository.save(vehicleType);
    }

    public void deleteVehicleType(Long id) {
        VehicleType vehicleType = getVehicleTypeById(id);
        vehicleTypeRepository.delete(vehicleType);
    }
}
