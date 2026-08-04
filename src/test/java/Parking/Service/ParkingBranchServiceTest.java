package Parking.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import Parking.Model.ParkingBranch;
import Parking.Repository.BookingRepository;
import Parking.Repository.ParkingBranchRepository;
import Parking.Repository.ParkingSessionRepository;
import Parking.Repository.ParkingZoneRepository;
import Parking.dto.response.ParkingCapacityResponse;
import Parking.enums.ParkingSessionStatus;

@ExtendWith(MockitoExtension.class)
class ParkingBranchServiceTest {

    @Mock private ParkingBranchRepository parkingBranchRepository;
    @Mock private ParkingZoneRepository parkingZoneRepository;
    @Mock private ParkingSessionRepository parkingSessionRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BranchScopeService branchScopeService;

    @InjectMocks private ParkingBranchService parkingBranchService;

    @Test
    void getMyBranchCapacity_shouldReturnCapacityForAssignedBranch() {
        ParkingBranch branch = new ParkingBranch();
        branch.setParkingBranchId(12L);
        branch.setBranchName("Chi nhánh Trung tâm");

        when(branchScopeService.resolveReadableBranchId(null)).thenReturn(12L);
        when(parkingBranchRepository.findById(12L)).thenReturn(Optional.of(branch));
        when(parkingZoneRepository.calculateTotalCapacityByBranch(12L)).thenReturn(100L);
        when(parkingSessionRepository.countByParkingBranchParkingBranchIdAndStatus(
                12L, ParkingSessionStatus.ACTIVE)).thenReturn(65L);
        when(bookingRepository.countActiveBookingsByBranch(any(Long.class), any(LocalDateTime.class)))
                .thenReturn(10L);

        ParkingCapacityResponse response = parkingBranchService.getMyBranchCapacity();

        assertEquals(12L, response.getParkingBranchId());
        assertEquals("Chi nhánh Trung tâm", response.getBranchName());
        assertEquals(100, response.getTotalCapacity());
        assertEquals(65, response.getOccupiedCapacity());
        assertEquals(10, response.getReservedCapacity());
        assertEquals(25, response.getAvailableCapacity());
    }

    @Test
    void getMyBranchCapacity_shouldNotReturnNegativeAvailableCapacity() {
        ParkingBranch branch = new ParkingBranch();
        branch.setParkingBranchId(12L);
        branch.setBranchName("Chi nhánh Trung tâm");

        when(branchScopeService.resolveReadableBranchId(null)).thenReturn(12L);
        when(parkingBranchRepository.findById(12L)).thenReturn(Optional.of(branch));
        when(parkingZoneRepository.calculateTotalCapacityByBranch(12L)).thenReturn(5L);
        when(parkingSessionRepository.countByParkingBranchParkingBranchIdAndStatus(
                12L, ParkingSessionStatus.ACTIVE)).thenReturn(5L);
        when(bookingRepository.countActiveBookingsByBranch(any(Long.class), any(LocalDateTime.class)))
                .thenReturn(2L);

        ParkingCapacityResponse response = parkingBranchService.getMyBranchCapacity();

        assertEquals(0, response.getAvailableCapacity());
    }
}
