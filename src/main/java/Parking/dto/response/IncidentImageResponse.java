package Parking.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class IncidentImageResponse {
    private Long incidentImageId;
    private Long incidentId;
    private String imageUrl;
    private LocalDateTime uploadedAt;
    private Long uploadedById;
    private String uploadedByName;
}
