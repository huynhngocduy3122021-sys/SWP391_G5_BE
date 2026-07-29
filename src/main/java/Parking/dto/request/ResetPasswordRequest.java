package Parking.dto.request;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
public class ResetPasswordRequest {
    @NotBlank(message = "Email hoặc số điện thoại là bắt buộc")
    private String emailOrPhone;

    @NotBlank(message = "Mật khẩu mới là bắt buộc")
    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String newPassword;

    @NotBlank(message = "Xác nhận mật khẩu là bắt buộc")
    private String confirmPassword;
}
