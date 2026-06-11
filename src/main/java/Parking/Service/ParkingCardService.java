package Parking.Service;

import org.springframework.stereotype.Service;

import Parking.Model.ParkingCard;
import Parking.Repository.ParkingCardRepository;
import Parking.dto.request.CreateParkingCardRequest;
import Parking.enums.ParkingCardStatus;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParkingCardService {
     private final ParkingCardRepository parkingCardRepository;

    public ParkingCard createParkingCard(CreateParkingCardRequest request) {
        ParkingCard parkingCard = new ParkingCard();
        parkingCard.setCardCode(request.getCardCode());
        parkingCard.setStatus(ParkingCardStatus.AVAILABLE);

        return parkingCardRepository.save(parkingCard);
    }

    public List<ParkingCard> getAllParkingCards() {
        return parkingCardRepository.findAll();
    }
}
