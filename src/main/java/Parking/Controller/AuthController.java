package Parking.Controller;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.beans.factory.annotation.Autowired;
import Parking.Service.UserService;
import org.springframework.web.bind.annotation.RequestBody;
import Parking.dto.request.UserRequest;
import Parking.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import Parking.dto.request.LoginRequest;
import Parking.dto.request.UpdateUserRequest;
import Parking.dto.request.StaffCreateRequest;
import Parking.dto.request.ManagerCreateRequest;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import Parking.dto.request.ChangePasswordRequest;
import Parking.dto.request.ResetPasswordRequest;
import org.springframework.web.bind.annotation.DeleteMapping;

/**
 * Controller chuyên xử lý các API liên quan đến Xác thực (Authentication) và Quản lý Người Dùng (User).
 */
@RestController
@RequestMapping("/api/auth") // Tiền tố URL chung cho nhóm API bảo mật/đăng nhập
@CrossOrigin(origins = "*") // Cho phép các domain khác (Frontend) gọi API (tránh lỗi CORS)
@SecurityRequirement(name = "api_key") // Yêu cầu có token khi test bằng Swagger
public class AuthController {
    
    // Tự động tiêm UserService vào để gọi các hàm xử lý logic
    @Autowired
    private UserService userService;

    /**
     * Khách hàng đăng ký tài khoản mới (Role mặc định sẽ được gán là USER).
     */
    @PostMapping("/register")
    @Operation(summary = "hàm đăng kí user", description = "Đăng kí tài khoảng hệ thông role mặc đi là USER")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRequest registerRequest) {
        UserResponse userResponse = userService.register(registerRequest);
        return ResponseEntity.ok(userResponse);
    }
    
    /**
     * Admin tạo tài khoản (Có thể chọn Role tùy ý: USER, STAFF, MANAGER).
     * Chỉ ADMIN mới gọi được API này.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin-create")
    @Operation(summary = "Hàm dùng cho admin tạo user với role tuỳ chọn", description = "Admin có thể tạo STAFF, MANAGER, USER")
    public ResponseEntity<UserResponse> adminCreateUser(@Valid @RequestBody Parking.dto.request.AdminCreateUserRequest request) {
        UserResponse userResponse = userService.adminCreateUser(request);
        return ResponseEntity.ok(userResponse);
    }

    /**
     * Đăng nhập vào hệ thống (Trả về UserResponse kèm theo Token).
     */
    @PostMapping("/login")
    @Operation(summary = "Hàm dùng để login vào hệ thống")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        UserResponse userResponse = userService.login(loginRequest);
        return ResponseEntity.ok(userResponse);
    }
    
    /**
     * Admin tạo tài khoản cho NHÂN VIÊN bãi đỗ (STAFF).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/staff")
    @Operation(summary = "Tạo tài khoản STAFF", description = "Chỉ Admin mới có quyền tạo tài khoản STAFF và gán chi nhánh")
    public ResponseEntity<UserResponse> createStaff(@Valid @RequestBody StaffCreateRequest request) {
        UserResponse userResponse = userService.createStaff(request);
        return ResponseEntity.ok(userResponse);
    }

    /**
     * Admin tạo tài khoản cho QUẢN LÝ bãi đỗ (MANAGER).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/manager")
    @Operation(summary = "Tạo tài khoản MANAGER", description = "Chỉ Admin mới có quyền tạo tài khoản MANAGER và gán chi nhánh")
    public ResponseEntity<UserResponse> createManager(@Valid @RequestBody ManagerCreateRequest request) {
        UserResponse userResponse = userService.createManager(request);
        return ResponseEntity.ok(userResponse);
    }
    
    /**
     * Lấy toàn bộ danh sách người dùng trong hệ thống (Chỉ Admin).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    @Operation(summary = "Hàm dùng để lấy tất cả dữ liệu của những người dùng", description = "Chỉ có admin có quyền lấy dữ liệu người dùng")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * Lấy thông tin chi tiết của 1 User dựa theo ID.
     */
    @GetMapping("/users/{id}")
    @Operation(summary = "Lấy dữ liệu người dùng theo Id")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse userResponse = userService.getUserById(id);
        return ResponseEntity.ok(userResponse);
    }

    /**
     * API Thay đổi mật khẩu người dùng.
     */
    @PutMapping("/users/{id}/change-password") // change password
    @Operation(summary = "Thay đổi mật khẩu")
    public ResponseEntity<String> changePassword(@PathVariable Long id, @RequestBody ChangePasswordRequest changePasswordRequest) {
        userService.updatePassword(id, changePasswordRequest);
        return ResponseEntity.ok("Password changed successfully");
    }

    /**
     * API Cập nhật thông tin cơ bản của người dùng (Tên, SĐT...).
     */
    @PutMapping("/users/{id}") // update info user
    @Operation(summary = "Cập nhật thông tin user")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest userRequest) {
        UserResponse updatedUser = userService.updateUser(id, userRequest);
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * Xóa tài khoản người dùng (Chỉ Admin thực hiện).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/users/{id}") // delete user
    @Operation(summary = "Xóa user", description = "Chức năng chỉ có admin")
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted successfully");
    }

    /**
     * Quên mật khẩu / Reset lại mật khẩu.
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Lấy lại mật khẩu user")
    public ResponseEntity<UserResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        UserResponse userResponse = userService.resetPassword(resetPasswordRequest);
        return ResponseEntity.ok(userResponse);
    }
}
