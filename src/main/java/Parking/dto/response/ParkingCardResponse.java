package Parking.dto.response;

import Parking.enums.ParkingCardStatus;
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

    private Long parkingBranchId;

    private String parkingBranchName;
}