package Parking.dto.response;

import java.time.LocalDateTime;

import Parking.Model.VehicleImageType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class VehicleImageResponse {
    private Long vehicleImageId;

    private Long parkingSessionId;

    private String imageUrl;

    private VehicleImageType imageType;

    private LocalDateTime uploadedAt;
}
