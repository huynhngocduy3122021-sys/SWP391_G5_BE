package Parking.service;

import org.springframework.stereotype.Service;
import Parking.dto.request.LoginRequest;
import Parking.dto.request.UserRequest;
import Parking.dto.response.UserResponse;
import Parking.enums.UserRole;
import Parking.model.User;
import Parking.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.Authentication;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Lazy;

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
            throw new RuntimeException("Email already exists");
        }
        // Kiểm tra xem số điện thoại đã tồn tại hay chưa
        if (userRepository.existsByUserPhone(registerRequest.getUserPhone())) {
            throw new RuntimeException("Phone number already exists");
        }
        // Mã hóa mật khẩu trước khi lưu vào database
        registerRequest.setUserPassword(passwordEncoder.encode(registerRequest.getUserPassword()));
        // Tạo đối tượng User từ UserRequest
        User newUser = modelMapper.map(registerRequest, User.class);
        User savedUser = userRepository.save(newUser); // Lưu người dùng vào database
        

        return convertToResponse(savedUser);
        
        
    } 

    public UserResponse login(LoginRequest loginRequest) {
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getUserEmail(), loginRequest.getUserPassword())
        );
        User user = (User) auth.getPrincipal();
        UserResponse userResponse = modelMapper.map(user, UserResponse.class);
        String token =  tokenService.generateToken(user);
        userResponse.setToken(token);

        
        return userResponse;
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
        return response;
    }
    
    
}
