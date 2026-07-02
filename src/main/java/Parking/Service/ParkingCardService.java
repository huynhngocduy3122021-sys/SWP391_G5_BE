package Parking.Service;

import org.springframework.stereotype.Service;

import Parking.Model.ParkingBranch;
import Parking.Model.ParkingCard;
import Parking.Repository.ParkingBranchRepository;
import Parking.Repository.ParkingCardRepository;
import Parking.dto.request.CreateParkingCardRequest;
import Parking.dto.request.UpdateParkingCardRequest;
import Parking.dto.response.ParkingCardResponse;
import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingCardType;
import Parking.exception.exceptions.ParkingSessionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ParkingCardService {
    private final ParkingCardRepository parkingCardRepository;
    private final ParkingBranchRepository parkingBranchRepository;

    @Transactional
    public ParkingCardResponse createParkingCard(CreateParkingCardRequest request) {
        String cardCode = request.getCardCode().trim();

        boolean cardCodeExists = parkingCardRepository.existsByCardCode(cardCode);

        if (cardCodeExists) {
            throw new ParkingSessionException("Mã thẻ giữ xe đã tồn tại");
        }

        ParkingBranch parkingBranch = parkingBranchRepository.findById(request.getParkingBranchId())
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy chi nhánh bãi xe"));

        if (!parkingBranch.isActive()) {
            throw new ParkingSessionException("Chi nhánh bãi xe đang ngừng hoạt động");
        }

        ParkingCard parkingCard = new ParkingCard();
        parkingCard.setCardCode(cardCode);
        parkingCard.setStatus(ParkingCardStatus.AVAILABLE);
        if (request.getType() != null) {
            parkingCard.setType(request.getType());
        }
        parkingCard.setParkingBranch(parkingBranch);

        return convertToResponse(parkingCardRepository.save(parkingCard));
    }

    @Transactional(readOnly = true)
    public List<ParkingCardResponse> getAllParkingCards() {
        return parkingCardRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkingCardResponse getParkingCardById(Long id) {
        return convertToResponse(findParkingCard(id));
    }

    @Transactional
    public ParkingCardResponse updateParkingCard(Long id, UpdateParkingCardRequest request) {
        ParkingCard parkingCard = findParkingCard(id);

        if (request.getCardCode() != null && !request.getCardCode().isBlank()) {
            String cardCode = request.getCardCode().trim();
            if (!parkingCard.getCardCode().equalsIgnoreCase(cardCode) && parkingCardRepository.existsByCardCode(cardCode)) {
                throw new ParkingSessionException("Mã thẻ giữ xe đã tồn tại");
            }
            parkingCard.setCardCode(cardCode);
        }

        if (request.getParkingBranchId() != null) {
            ParkingBranch parkingBranch = parkingBranchRepository.findById(request.getParkingBranchId())
                    .orElseThrow(() -> new ParkingSessionException("Không tìm thấy chi nhánh bãi xe"));
            if (!parkingBranch.isActive()) {
                throw new ParkingSessionException("Chi nhánh bãi xe đang ngừng hoạt động");
            }
            parkingCard.setParkingBranch(parkingBranch);
        }

        if (request.getStatus() != null) {
            parkingCard.setStatus(request.getStatus());
        }

        if (request.getType() != null) {
            parkingCard.setType(request.getType());
        }

        return convertToResponse(parkingCardRepository.save(parkingCard));
    }

    @Transactional
    public void deleteParkingCard(Long id) {
        ParkingCard parkingCard = findParkingCard(id);
        parkingCardRepository.delete(parkingCard);
    }

    private ParkingCard findParkingCard(Long id) {
        return parkingCardRepository.findById(id)
                .orElseThrow(() -> new ParkingSessionException("Không tìm thấy thẻ giữ xe"));
    }

    private ParkingCardResponse convertToResponse(ParkingCard parkingCard) {
        ParkingBranch parkingBranch = parkingCard.getParkingBranch();

        return ParkingCardResponse.builder()
                .parkingCardId(parkingCard.getParkingCardId())
                .cardCode(parkingCard.getCardCode())
                .status(parkingCard.getStatus())
                .type(parkingCard.getType())
                .parkingBranchId(parkingBranch != null ? parkingBranch.getParkingBranchId() : null)
                .parkingBranchName(parkingBranch != null ? parkingBranch.getBranchName() : null)
                .build();
    }
}
