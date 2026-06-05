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
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import Parking.dto.request.LoginRequest;
import Parking.dto.request.UpdateUserRequest;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import Parking.dto.request.ChangePasswordRequest;
import Parking.dto.request.ResetPasswordRequest;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")

public class AuthController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRequest registerRequest) {
        UserResponse userResponse = userService.register(registerRequest);
        return ResponseEntity.ok(userResponse);
    }
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        UserResponse userResponse = userService.login(loginRequest);
        return ResponseEntity.ok(userResponse);
    }
    @PostMapping("/reset-password")
    public ResponseEntity<UserResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        UserResponse userResponse = userService.resetPassword(resetPasswordRequest);
        return ResponseEntity.ok(userResponse);
    }
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse userResponse = userService.getUserById(id);
        return ResponseEntity.ok(userResponse);
    }
        @PutMapping("/users/{id}/change-password") // change password
    public ResponseEntity<String> changePassword(@PathVariable Long id, @RequestBody ChangePasswordRequest changePasswordRequest) {
        userService.updatePassword(id, changePasswordRequest);
        return ResponseEntity.ok("Password changed successfully");
    }

    @PutMapping("/users/{id}") // update info user
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest userRequest) {
        UserResponse updatedUser = userService.updateUser(id, userRequest);
        return ResponseEntity.ok(updatedUser);
    }
    @DeleteMapping("/users/{id}") // delete user
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted successfully");
    }
    
    
}
