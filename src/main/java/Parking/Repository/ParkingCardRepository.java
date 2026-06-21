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
}
