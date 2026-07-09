package Parking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManagerCreateRequest {
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
    
    @NotNull(message = "Parking branch is required")
    private Long parkingBranchId;
    
    private String userAddress;
}
