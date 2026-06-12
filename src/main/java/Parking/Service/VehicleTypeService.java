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
}
