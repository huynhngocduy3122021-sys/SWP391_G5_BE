package Parking.dto.request;

import Parking.enums.UserRole;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

@Getter
@Setter
public class AdminCreateUserRequest {
    @NotBlank(message = "Họ và tên là bắt buộc")
    private String userFullName;
    
    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Định dạng email không hợp lệ")
    private String userEmail;
    
    @NotBlank(message = "Mật khẩu là bắt buộc")
    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String userPassword;
    
    @NotBlank(message = "Số điện thoại là bắt buộc")
    private String userPhone;
    
    private String userAddress;
    
    @NotNull(message = "Vai trò là bắt buộc")
    private UserRole userRole;
}
