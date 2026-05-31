package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter 
public class ChangePasswordRequest {
    @NotBlank(message = "You need to enter your password")
    private String oldPassword;
    @NotBlank(message = "You need to enter your new password")
    private String newPassword;
    @NotBlank(message = "You need to confirm password")
    private String confirmPassword;
}
