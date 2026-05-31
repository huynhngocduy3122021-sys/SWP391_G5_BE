package Parking.service;

import org.springframework.stereotype.Service;
import Parking.dto.request.LoginRequest;
import Parking.dto.request.UpdateUserRequest;
import Parking.dto.request.UserRequest;
import Parking.dto.response.UserResponse;
import Parking.model.User;
import Parking.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.Authentication;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Lazy;
import java.util.List;
import java.util.stream.Collectors;
import Parking.exception.exceptions.AuthenticationException;
import Parking.dto.request.ChangePasswordRequest;

@Service
public class UserService implements UserDetailsService  {
    @Autowired
    private  UserRepository userRepository; // gọi repository để thao tác với database
    @Autowired
    private  PasswordEncoder passwordEncoder;
    @Autowired
    @Lazy
    private AuthenticationManager authenticationManager;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private TokenService tokenService;
    
    public UserResponse register(UserRequest registerRequest) {
        // Kiểm tra xem email đã tồn tại hay chưa
        if (userRepository.existsByUserEmail(registerRequest.getUserEmail())) {
            throw new AuthenticationException("Email already exists");
        }
        // Kiểm tra xem số điện thoại đã tồn tại hay chưa
        if (userRepository.existsByUserPhone(registerRequest.getUserPhone())) {
            throw new AuthenticationException("Phone number already exists");
        }
        // Mã hóa mật khẩu trước khi lưu vào database
        registerRequest.setUserPassword(passwordEncoder.encode(registerRequest.getUserPassword()));
        // Tạo đối tượng User từ UserRequest
        User newUser = modelMapper.map(registerRequest, User.class);
        User savedUser = userRepository.save(newUser); // Lưu người dùng vào database
        

        return convertToResponse(savedUser);
        
        
    } 

        public UserResponse login(LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUserEmail(),
                            loginRequest.getUserPassword()
                    )
            );

            User user = (User) authentication.getPrincipal();

            UserResponse userResponse = convertToResponse(user);

            String token = tokenService.generateToken(user);
            userResponse.setToken(token);

            return userResponse;

        } catch (LockedException e) {
            throw new AuthenticationException("Tài khoản đã bị khóa");

        } catch (DisabledException e) {
            throw new AuthenticationException("Tài khoản đã bị vô hiệu hóa");

        } catch (BadCredentialsException e) {
            throw new AuthenticationException("Email hoặc mật khẩu không đúng");

        } catch (Exception e) {
            throw new AuthenticationException("Đăng nhập thất bại");
        }
    }

    @Override
    public User loadUserByUsername(String email) {
        return userRepository.findByUserEmail(email);
    }

    private UserResponse convertToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setUserFullName(user.getUserFullName());
        response.setUserEmail(user.getUserEmail());
        response.setUserPhone(user.getUserPhone());
        response.setUserAddress(user.getUserAddress());
        response.setUserRole(user.getUserRole().name());
        response.setDeleted(user.isDeleted());
        return response;
    }
    // get all user
    public List<UserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
                    .map(this::convertToResponse) // chuyển đổi từng User thành UserResponse
                    .collect(Collectors.toList()); // thu thập kết quả vào một List<UserResponse> và trả về
    }
    // get user by id
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> new AuthenticationException("User not found"));

        if(user.isDeleted()) {
            throw new AuthenticationException("User is deleted");
        } else {
            return convertToResponse(user);
        }
    
    }

    //update user
    public UserResponse updateUser(Long userId, UpdateUserRequest userRequest) { // Tìm người dùng cần cập nhật theo ID, nếu không tìm thấy thì quăng ra lỗi (RuntimeException)
        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> new AuthenticationException("User not found"));
                                 
            if(userRequest.getUserFullName() != null && !userRequest.getUserFullName().isBlank()) {
                user.setUserFullName(userRequest.getUserFullName());
            }
            if (userRequest.getUserEmail() != null && !userRequest.getUserEmail().isBlank()) {
            user.setUserEmail(userRequest.getUserEmail());
                }

            if (userRequest.getUserPhone() != null && !userRequest.getUserPhone().isBlank()) {
                user.setUserPhone(userRequest.getUserPhone());
            }

            if (userRequest.getUserAddress() != null && !userRequest.getUserAddress().isBlank()) {
                user.setUserAddress(userRequest.getUserAddress());
            }

            User updatedUser = userRepository.save(user);

            return convertToResponse(updatedUser);

    }
    // update password
    public UserResponse updatePassword(Long userId, ChangePasswordRequest changePasswordRequest) {
        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> new AuthenticationException("User not found"));
        // kiểm tra mật khẩu cũ có đúng không   
        if(!passwordEncoder.matches(changePasswordRequest.getOldPassword(), user.getUserPassword())) {
            throw new AuthenticationException("Old password is incorrect");
        }
        // kiểm tra mật khẩu mới có trùng với mật khẩu cũ không
        if(passwordEncoder.matches(changePasswordRequest.getNewPassword(), user.getUserPassword())) {
            throw new AuthenticationException("New password must be different from old password");
        }
        // kiểm tra mật khẩu mới và xác nhận mật khẩu có khớp nhau không
        if(!changePasswordRequest.getNewPassword().equals(changePasswordRequest.getConfirmPassword())) {
            throw new AuthenticationException("New password and confirm password do not match");
        }
        // mã hóa mật khẩu mới và lưu vào database
        user.setUserPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        User updatedUser = userRepository.save(user);
        return convertToResponse(updatedUser);
    }

    // delete user
    public UserResponse deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> new AuthenticationException("User not found"));
                                
       
         // tạm thời cho hàm này để chuyển đổi giữa tài khoảng xoá hay chưa xoá (demo)
        
        user.setDeleted(!user.isDeleted());
        
        userRepository.save(user);
        return convertToResponse(user);

    }
}
