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
    
    @NotNull(message = "Chi nhánh bãi xe là bắt buộc")
    private Long parkingBranchId;
    
    private String userAddress;
}
