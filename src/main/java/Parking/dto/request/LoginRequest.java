package Parking.dto.request;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
@Getter
@Setter

public class LoginRequest {
    
    @NotBlank(message = "Email or phone is required")
    private String identifier;
    @NotBlank(message = "Password is required")
    private String userPassword;
}
