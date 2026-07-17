package Parking.Repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import Parking.Model.ParkingCard;
import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingCardType;

public interface ParkingCardRepository extends JpaRepository<ParkingCard , Long> {
    Optional<ParkingCard> findByCardCodeIgnoreCase(String cardCode);

    boolean existsByCardCode(String cardCode);

    java.util.List<ParkingCard> findByParkingBranchParkingBranchId(Long parkingBranchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParkingCard> findFirstByParkingBranchParkingBranchIdAndStatusAndTypeOrderByParkingCardIdAsc(
        Long branchId,
        ParkingCardStatus status,
        ParkingCardType type
    );

    default java.util.Optional<ParkingCard> findFirstAvailableMonthlyCard(Long branchId) {
        return findFirstByParkingBranchParkingBranchIdAndStatusAndTypeOrderByParkingCardIdAsc(
            branchId,
            ParkingCardStatus.AVAILABLE,
            ParkingCardType.MONTHLY
        );
    }
}
