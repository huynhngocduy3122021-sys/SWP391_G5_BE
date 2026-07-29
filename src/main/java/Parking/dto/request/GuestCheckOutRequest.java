package Parking.dto.request;

import java.time.LocalDateTime;

import Parking.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuestCheckOutRequest {

    @NotBlank(message = "Mã thẻ là bắt buộc")
    @Size(min = 3, max = 50, message = "Mã thẻ phải có từ 3 đến 50 ký tự")
    @Pattern(
            regexp = "^[A-Za-z0-9\\-_]+$",
            message = "Mã thẻ chỉ được chứa chữ cái, chữ số, dấu '-' hoặc dấu '_'"
    )
     private String cardCode;

    private PaymentMethod paymentMethod;
    private String licensePlate;
    private Boolean lostCard;
    private LocalDateTime time;
}
