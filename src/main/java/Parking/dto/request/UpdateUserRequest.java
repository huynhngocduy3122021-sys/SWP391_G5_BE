package Parking.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

@Setter
@Getter
public class UpdateUserRequest {
    
   @NotBlank(message = "Full name is required")
    private String userFullName;
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String userEmail;
    @NotBlank(message = "Password is required")
    @NotBlank(message = "Phone number is required")
    private String userPhone;
    @NotBlank(message = "Address is required")  
    private String userAddress;
}
