package Parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter 
public class ChangePasswordRequest {
    @NotBlank(message = "Bạn cần nhập mật khẩu hiện tại")
    private String oldPassword;
    @NotBlank(message = "Bạn cần nhập mật khẩu mới")
    private String newPassword;
    @NotBlank(message = "Bạn cần nhập xác nhận mật khẩu")
    private String confirmPassword;
}
