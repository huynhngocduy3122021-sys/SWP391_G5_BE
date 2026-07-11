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

    @NotBlank(message = "Card code is required")
    @Size(min = 3, max = 50, message = "Card code must be between 3 and 50 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9\\-_]+$",
            message = "Card code can only contain letters, numbers, '-' or '_'"
    )
     private String cardCode;

    private PaymentMethod paymentMethod;
    private String licensePlate;
    private Boolean lostCard;
    private LocalDateTime time;
}
