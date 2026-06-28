package Parking.dto.request;

import Parking.enums.IncidentPriority;
import Parking.enums.IncidentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class CreateIncidentRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotBlank(message = "Mô tả chi tiết không được để trống")
    private String description;

    @NotNull(message = "Loại sự cố là bắt buộc")
    private IncidentType incidentType;

    private Long parkingBranchId;

    private Long parkingSessionId; 

    private Long parkingCardId; 

    private String locationDetails; // Vị trí cụ thể của sự cố

    private IncidentPriority priority = IncidentPriority.MEDIUM;

    private List<ImageDto> images; // List các ảnh đã upload trước đó

    @Getter
    @Setter
    public static class ImageDto {
        private String imageUrl;
        private String publicId;
    }
}
