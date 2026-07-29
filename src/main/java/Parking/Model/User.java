package Parking.Model;

import Parking.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
import jakarta.validation.constraints.*;


/**
 * Entity đại diện cho bảng "users" trong Database.
 * Kế thừa UserDetails của Spring Security để giúp hệ thống tự động nhận diện đây là bảng dùng để Đăng Nhập.
 */
@Entity
@Getter
@Setter
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId; // Mã người dùng
    
    @Column(name = "user_full_name", columnDefinition = "nvarchar(255)", nullable = false)
    @NotEmpty(message = "Họ và tên là bắt buộc")
    private String userFullName; // Họ và tên
  
    @Column(name = "user_email" , columnDefinition = "NVARCHAR(255)", unique = true)
    @Email
    @NotEmpty(message = "Email không được để trống!")
    private String userEmail; // Email dùng để đăng nhập (Phải là duy nhất - unique = true)

    @Column(name = "user_password", nullable = false)
    private String userPassword; // Mật khẩu (đã bị băm/mã hóa bằng BCrypt)

    @Column(name = "user_phone", unique = true)
    @Pattern(regexp = "(03|05|07|08|09|012|016|018|019)[0-9]{8}$", message = "Số điện thoại không hợp lệ!")
    @NotEmpty(message = "Số điện thoại không được để trống!" )
    private String userPhone; // Số điện thoại (Cũng có thể dùng để đăng nhập, unique = true)
    
    @Column(name = "user_address", columnDefinition = "nvarchar(255)")
    private String userAddress;

    // Quyền hạn của người dùng (Mặc định khi đăng ký tự do là USER)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole userRole = UserRole.USER;
    
    @Column
    private boolean deleted = false; // Đánh dấu xóa mềm (Soft delete)

    @Column(name = "violation_count", nullable = false, columnDefinition = "int default 0")
    private int violationCount = 0; // Đếm số lần vi phạm (Ví dụ: đặt giữ chỗ nhưng không tới)

    @Column(name = "is_locked", nullable = false, columnDefinition = "bit default 0")
    private boolean locked = false; // Trạng thái khóa tài khoản (Nếu vi phạm quá 3 lần)

    // Nếu người này là Nhân viên/Quản lý, họ thuộc về bãi giữ xe nào?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_branch_id")
    private ParkingBranch parkingBranch;

    // --- CÁC HÀM BẮT BUỘC CỦA SPRING SECURITY ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Cấp quyền cho user, thêm tiền tố "ROLE_" theo chuẩn Spring Security (VD: ROLE_ADMIN, ROLE_USER)
        return List.of(new SimpleGrantedAuthority("ROLE_" + userRole.name()));
    }

    @Override
    public String getPassword() { 
        return userPassword;
    }

    @Override
    public String getUsername() { 
        // Spring Security sẽ dùng hàm này để lấy "tên đăng nhập". Ở đây ta dùng Email.
        return userEmail;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Bỏ qua chức năng hết hạn tài khoản
    }

    @Override
    public boolean isAccountNonLocked() { 
        // Hàm này dùng để biết tài khoản có bị khóa không. Không cho phép đăng nhập nếu đã bị xóa, bị khóa thủ công hoặc vi phạm >= 3 lần
        return !deleted && !locked && violationCount < 3;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Bỏ qua chức năng hết hạn mật khẩu
    }

    @Override
    public boolean isEnabled() {
        return !deleted; // Tài khoản được phép hoạt động nếu chưa bị xóa mềm
    }
}
