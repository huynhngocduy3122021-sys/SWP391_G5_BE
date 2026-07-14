package Parking.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import Parking.Model.ParkingCard;
public interface ParkingCardRepository extends JpaRepository<ParkingCard , Long> {
    Optional<ParkingCard> findByCardCodeIgnoreCase (String cardCode);

    boolean existsByCardCode(String cardCode);

    Optional<ParkingCard> findByCardCode(String cardCode);

    Optional<ParkingCard>
    findByCardCodeAndParkingBranchParkingBranchId(
            String cardCode,
            Long parkingBranchId
    );

    java.util.List<ParkingCard> findByParkingBranchParkingBranchId(Long parkingBranchId);

    @org.springframework.data.jpa.repository.Query("""
        SELECT pc FROM ParkingCard pc
        WHERE pc.parkingBranch.parkingBranchId = :branchId
          AND pc.status = Parking.enums.ParkingCardStatus.AVAILABLE
          AND pc.type = Parking.enums.ParkingCardType.MONTHLY
    """)
    java.util.List<ParkingCard> findAvailableMonthlyCardsByBranch(
        @org.springframework.data.repository.query.Param("branchId") Long branchId
    );

    default java.util.Optional<ParkingCard> findFirstAvailableMonthlyCard(Long branchId) {
        return findAvailableMonthlyCardsByBranch(branchId).stream().findFirst();
    }
}
