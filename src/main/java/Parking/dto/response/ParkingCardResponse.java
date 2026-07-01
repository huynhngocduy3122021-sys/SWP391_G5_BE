package Parking.dto.response;

import Parking.enums.ParkingCardStatus;
import Parking.enums.ParkingCardType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ParkingCardResponse {

    private Long parkingCardId;

    private String cardCode;

    private ParkingCardStatus status;

    private ParkingCardType type;

    private Long parkingBranchId;

    private String parkingBranchName;
}