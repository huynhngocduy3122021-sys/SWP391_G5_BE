package Parking.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class CreatePricePolicyRequest {
    @NotBlank(message = "Tên chính sách giá là bắt buộc")
    @Size(max = 255, message = "Tên chính sách giá không được vượt quá 255 ký tự")
    private String policyName;

    @NotNull(message = "Giá cơ bản là bắt buộc")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá cơ bản phải lớn hơn 0")
    private BigDecimal basePrice;

    @NotNull(message = "Thời lượng cơ bản là bắt buộc")
    @Positive(message = "Thời lượng cơ bản phải lớn hơn 0 phút")
    private Integer baseDurationMinutes;

    @NotNull(message = "Giá mỗi giờ phát sinh là bắt buộc")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá mỗi giờ phát sinh phải lớn hơn hoặc bằng 0")
    private BigDecimal extraHourPrice;

    private Integer extraDurationMinutes = 60;

    @NotNull(message = "ID loại phương tiện là bắt buộc")
    @Positive(message = "ID loại phương tiện phải lớn hơn 0")
    private Long vehicleTypeId;
}
