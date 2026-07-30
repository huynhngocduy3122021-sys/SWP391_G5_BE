package Parking.dto.request;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
public class VerifyOtpRequest {
    @NotBlank(message = "Identifier là bắt buộc")
    private String identifier;
    
    @NotBlank(message = "OTP là bắt buộc")
    private String otp;
}
