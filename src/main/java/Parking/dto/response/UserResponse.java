package Parking.dto.response;

import lombok.Getter;
import lombok.Setter;
/**
 * Lớp DTO (Data Transfer Object) dùng để đóng gói thông tin User trước khi trả về cho Frontend.
 * Hậu tố "Response" ám chỉ đây là cục dữ liệu Backend gửi về cho Frontend.
 * 
 * Lưu ý: Chúng ta KHÔNG BAO GIỜ để thuộc tính "password" ở đây, vì việc trả mật khẩu về cho Frontend là lỗi bảo mật nghiêm trọng.
 */
@Getter
@Setter
public class UserResponse {
    
    private Long userId; // Mã ID người dùng
    private String userFullName; // Họ và tên
    private String userEmail; // Email
    private String userPhone; // Số điện thoại
    private String userAddress; // Địa chỉ
    private String userRole; // Quyền hạn (ADMIN, MANAGER, STAFF, USER)
    
    private boolean deleted; // Trạng thái đã bị xóa hay chưa
    private int violationCount; // Số lần vi phạm (Ví dụ: đặt chỗ mà không tới)
    private boolean locked; // Trạng thái tài khoản bị khóa
    
    private String token; // Chuỗi mã thông báo (JWT token) cấp cho Frontend sau khi đăng nhập thành công
    
    // Nếu user này là Nhân viên (Staff/Manager), họ sẽ được gắn vào một bãi đỗ cụ thể
    private Long parkingBranchId;
    private String parkingBranchName;

}
