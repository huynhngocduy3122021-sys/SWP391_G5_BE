package Parking.dto.request;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
/**
 * Lớp DTO (Data Transfer Object) chuyên dùng để hứng dữ liệu do người dùng nhập từ giao diện Đăng Nhập.
 * Hậu tố "Request" ám chỉ đây là cục dữ liệu đi từ Frontend gửi lên Backend.
 */
@Getter
@Setter
public class LoginRequest {
    
    // @NotBlank: Ràng buộc kiểm tra tính hợp lệ (Validation). 
    // Nếu người dùng không nhập gì hoặc chỉ nhập khoảng trắng, Spring Boot sẽ tự động chặn lại và báo lỗi.
    @NotBlank(message = "Email hoặc số điện thoại là bắt buộc")
    private String identifier; // Dùng 1 biến chung để chứa Email hoặc Số điện thoại
    
    @NotBlank(message = "Mật khẩu là bắt buộc")
    private String userPassword; // Mật khẩu người dùng nhập vào
}
