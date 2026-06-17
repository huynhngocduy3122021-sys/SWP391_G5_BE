package Parking.Service;

import org.springframework.stereotype.Service;

import Parking.Model.ParkingBranch;
import Parking.Model.ParkingCard;
import Parking.Repository.ParkingBranchRepository;
import Parking.Repository.ParkingCardRepository;
import Parking.dto.request.CreateParkingCardRequest;
import Parking.dto.response.ParkingCardResponse;
import Parking.enums.ParkingCardStatus;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParkingCardService {
     private final ParkingCardRepository parkingCardRepository;
     private final ParkingBranchRepository parkingBranchRepository;

    public ParkingCardResponse createParkingCard(CreateParkingCardRequest request) {
       String cardCode = request.getCardCode().trim();

        boolean cardCodeExists = parkingCardRepository.existsByCardCode(cardCode);

        if (cardCodeExists) {
            throw new ParkingSessionException( "Mã thẻ giữ xe đã tồn tại");
        }

        ParkingBranch parkingBranch = parkingBranchRepository.findById(request.getParkingBranchId())
                        .orElseThrow(() -> new ParkingSessionException("Không tìm thấy chi nhánh bãi xe"));

        if (!parkingBranch.isActive()) {
            throw new ParkingSessionException( "Chi nhánh bãi xe đang ngừng hoạt động");
        }

        ParkingCard parkingCard = new ParkingCard();

        parkingCard.setCardCode(cardCode);
        parkingCard.setStatus(ParkingCardStatus.AVAILABLE);
        parkingCard.setParkingBranch(parkingBranch);

        return convertToResponse(parkingCard);
    }

    public List<ParkingCardResponse> getAllParkingCards() {
        return parkingCardRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    private ParkingCardResponse convertToResponse(ParkingCard parkingCard) {
        ParkingBranch parkingBranch =
                parkingCard.getParkingBranch();

        return ParkingCardResponse.builder()
                .parkingCardId(parkingCard.getParkingCardId())
                .cardCode(parkingCard.getCardCode())
                .status(parkingCard.getStatus())
                .parkingBranchId( parkingBranch != null ? parkingBranch.getParkingBranchId(): null)
                .parkingBranchName( parkingBranch != null? parkingBranch.getBranchName(): null)
                .build();
    }
}
