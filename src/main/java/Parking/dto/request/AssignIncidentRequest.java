package Parking.dto.request;

import Parking.enums.IncidentPriority;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignIncidentRequest {
    @NotNull(message = "ID nhân viên phân công xử lý là bắt buộc")
    private Long assignedStaffId;

    private IncidentPriority priority;
}
