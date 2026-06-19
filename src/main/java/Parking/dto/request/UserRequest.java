package Parking.dto.request;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;


@Getter
@Setter
public class UserRequest {
    @NotBlank(message = "Full name is required")
    private String userFullName;
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String userEmail;
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String userPassword;
    @NotBlank(message = "Phone number is required")
    private String userPhone;
    // @NotBlank(message = "Address is required")  
    // private String userAddress;
  

   
}
