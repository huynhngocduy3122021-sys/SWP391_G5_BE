package Parking.Service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import Parking.Model.PricePolicy;
import Parking.Model.VehicleType;
import Parking.Repository.PricePolicyRepository;
import Parking.Repository.VehicleTypeRepository;
import Parking.dto.request.CreatePricePolicyRequest;
import Parking.dto.request.UpdatePricePolicyRequest;
import Parking.exception.exceptions.ParkingSessionException;

@ExtendWith(MockitoExtension.class)
class PricePolicyServiceTest {

    @Mock private PricePolicyRepository pricePolicyRepository;
    @Mock private VehicleTypeRepository vehicleTypeRepository;

    @InjectMocks private PricePolicyService pricePolicyService;

    @Test
    void createHourlyPolicy_shouldRejectVehicleTypeThatAlreadyHasHourlyPolicy() {
        CreatePricePolicyRequest request = createRequest("Giá gửi xe máy", 1L);
        PricePolicy existingPolicy = policy(10L);

        when(vehicleTypeRepository.findById(1L)).thenReturn(Optional.of(new VehicleType()));
        when(pricePolicyRepository.findHourlyPolicies(1L)).thenReturn(List.of(existingPolicy));

        assertThrows(ParkingSessionException.class,
                () -> pricePolicyService.createPricePolicy(request));
    }

    @Test
    void updateHourlyPolicy_shouldAllowEditingTheExistingPolicy() {
        UpdatePricePolicyRequest request = updateRequest("Giá gửi xe máy mới", 1L);
        PricePolicy existingPolicy = policy(10L);

        when(pricePolicyRepository.findById(10L)).thenReturn(Optional.of(existingPolicy));
        when(vehicleTypeRepository.findById(1L)).thenReturn(Optional.of(new VehicleType()));
        when(pricePolicyRepository.findHourlyPolicies(1L)).thenReturn(List.of(existingPolicy));
        when(pricePolicyRepository.save(any(PricePolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> pricePolicyService.updatePricePolicy(10L, request));
        verify(pricePolicyRepository).save(existingPolicy);
    }

    @Test
    void createPackagePolicy_shouldNotBeBlockedByHourlyPolicy() {
        CreatePricePolicyRequest request = createRequest("[Gói Tháng] Xe máy", 1L);

        when(vehicleTypeRepository.findById(1L)).thenReturn(Optional.of(new VehicleType()));
        when(pricePolicyRepository.save(any(PricePolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> pricePolicyService.createPricePolicy(request));
        verify(pricePolicyRepository).save(any(PricePolicy.class));
    }

    @Test
    void createPackagePolicy_shouldRejectVehicleTypeThatAlreadyHasPackage() {
        CreatePricePolicyRequest request = createRequest("[Gói Tháng] Xe máy", 1L);
        PricePolicy existingPackage = policy(20L);

        when(vehicleTypeRepository.findById(1L)).thenReturn(Optional.of(new VehicleType()));
        when(pricePolicyRepository.findPackagePolicies(1L)).thenReturn(List.of(existingPackage));

        assertThrows(ParkingSessionException.class,
                () -> pricePolicyService.createPricePolicy(request));
    }

    @Test
    void updatePackagePolicy_shouldAllowEditingTheExistingPackage() {
        UpdatePricePolicyRequest request = updateRequest("[Gói Tháng] Xe máy mới", 1L);
        PricePolicy existingPackage = policy(20L);

        when(pricePolicyRepository.findById(20L)).thenReturn(Optional.of(existingPackage));
        when(vehicleTypeRepository.findById(1L)).thenReturn(Optional.of(new VehicleType()));
        when(pricePolicyRepository.findPackagePolicies(1L)).thenReturn(List.of(existingPackage));
        when(pricePolicyRepository.save(any(PricePolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> pricePolicyService.updatePricePolicy(20L, request));
        verify(pricePolicyRepository).save(existingPackage);
    }

    private CreatePricePolicyRequest createRequest(String name, Long vehicleTypeId) {
        CreatePricePolicyRequest request = new CreatePricePolicyRequest();
        request.setPolicyName(name);
        request.setVehicleTypeId(vehicleTypeId);
        request.setBasePrice(BigDecimal.valueOf(5_000));
        request.setBaseDurationMinutes(60);
        request.setExtraHourPrice(BigDecimal.valueOf(2_000));
        request.setExtraDurationMinutes(60);
        return request;
    }

    private UpdatePricePolicyRequest updateRequest(String name, Long vehicleTypeId) {
        UpdatePricePolicyRequest request = new UpdatePricePolicyRequest();
        request.setPolicyName(name);
        request.setVehicleTypeId(vehicleTypeId);
        request.setBasePrice(BigDecimal.valueOf(6_000));
        request.setBaseDurationMinutes(60);
        request.setExtraHourPrice(BigDecimal.valueOf(2_000));
        request.setExtraDurationMinutes(60);
        request.setActive(true);
        return request;
    }

    private PricePolicy policy(Long id) {
        PricePolicy policy = new PricePolicy();
        policy.setPricePolicyId(id);
        return policy;
    }
}
