package Parking.dto.request;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
@Getter
@Setter

public class LoginRequest {
    
    @NotBlank(message = "Email hoặc số điện thoại là bắt buộc")
    private String identifier;
    @NotBlank(message = "Mật khẩu là bắt buộc")
    private String userPassword;
}
