package Parking.Service;

import org.springframework.stereotype.Service;
import Parking.dto.request.LoginRequest;
import Parking.dto.request.UpdateUserRequest;
import Parking.dto.request.UserRequest;
import Parking.dto.response.UserResponse;
import Parking.Model.User;
import Parking.Repository.UserRepository;
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
import Parking.dto.request.ResetPasswordRequest;
import Parking.Repository.ParkingBranchRepository;
import Parking.Model.ParkingBranch;
import Parking.dto.request.StaffCreateRequest;
import Parking.dto.request.ManagerCreateRequest;
import Parking.enums.UserRole;
import Parking.dto.request.VerifyOtpRequest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class UserService implements UserDetailsService  {
    private final Map<String, String> otpCache = new ConcurrentHashMap<>();
    @Autowired
    private  UserRepository userRepository; // gọi repository để thao tác với database
    @Autowired
    private ParkingBranchRepository parkingBranchRepository;
    @Autowired
    private  PasswordEncoder passwordEncoder;
    @Autowired
    @Lazy
    private AuthenticationManager authenticationManager;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private TokenService tokenService;
    
    /**
     * Đăng ký tài khoản người dùng mới (thường là Customer).
     * Luồng xử lý:
     * 1. Kiểm tra sự tồn tại của Email và Số điện thoại trong hệ thống.
     * 2. Nếu có trùng lặp, ném ra ngoại lệ AuthenticationException.
     * 3. Mã hóa mật khẩu do người dùng cung cấp.
     * 4. Map dữ liệu từ request sang đối tượng User và lưu vào cơ sở dữ liệu.
     * 5. Trả về thông tin người dùng.
     */
    public UserResponse register(UserRequest registerRequest) {
        // Kiểm tra xem email đã tồn tại hay chưa
        if (userRepository.existsByUserEmail(registerRequest.getUserEmail())) {
            throw new AuthenticationException("Email đã tồn tại");
        }
        // Kiểm tra xem số điện thoại đã tồn tại hay chưa
        if (userRepository.existsByUserPhone(registerRequest.getUserPhone())) {
            throw new AuthenticationException("Số điện thoại đã tồn tại");
        }
        // Mã hóa mật khẩu trước khi lưu vào database
        registerRequest.setUserPassword(passwordEncoder.encode(registerRequest.getUserPassword()));
        // Tạo đối tượng User từ UserRequest
        User newUser = modelMapper.map(registerRequest, User.class);
        User savedUser = userRepository.save(newUser); // Lưu người dùng vào database
        
        return convertToResponse(savedUser);
    } 

    /**
     * Admin tạo tài khoản người dùng với vai trò cụ thể.
     * Luồng xử lý:
     * 1. Kiểm tra email và số điện thoại đã tồn tại chưa.
     * 2. Nếu đã tồn tại, ném ra ngoại lệ AuthenticationException.
     * 3. Chuyển đổi dữ liệu từ request sang đối tượng User.
     * 4. Mã hóa mật khẩu và thiết lập vai trò (Role) cho người dùng mới.
     * 5. Lưu người dùng vào cơ sở dữ liệu và trả về thông tin.
     */
    public UserResponse adminCreateUser(Parking.dto.request.AdminCreateUserRequest request) {
        if (userRepository.existsByUserEmail(request.getUserEmail())) {
            throw new AuthenticationException("Email đã tồn tại");
        }
        if (userRepository.existsByUserPhone(request.getUserPhone())) {
            throw new AuthenticationException("Số điện thoại đã tồn tại");
        }
        
        User newUser = modelMapper.map(request, User.class);
        newUser.setUserPassword(passwordEncoder.encode(request.getUserPassword()));
        newUser.setUserRole(request.getUserRole());
        
        User savedUser = userRepository.save(newUser);
        return convertToResponse(savedUser);
    }

    /**
     * Tạo tài khoản nhân viên (Staff) và gán cho một chi nhánh (Branch) cụ thể.
     * Luồng xử lý:
     * 1. Kiểm tra trùng lặp email và số điện thoại.
     * 2. Tìm kiếm chi nhánh (ParkingBranch) theo ID. Nếu không tìm thấy, báo lỗi.
     * 3. Khởi tạo đối tượng User mới với các thông tin từ request.
     * 4. Mã hóa mật khẩu, gán vai trò là STAFF, gán chi nhánh đã tìm thấy.
     * 5. Lưu xuống cơ sở dữ liệu và trả về thông tin.
     */
    public UserResponse createStaff(StaffCreateRequest request) {
        if (userRepository.existsByUserEmail(request.getUserEmail())) {
            throw new AuthenticationException("Email đã tồn tại");
        }
        if (userRepository.existsByUserPhone(request.getUserPhone())) {
            throw new AuthenticationException("Số điện thoại đã tồn tại");
        }
        
        ParkingBranch branch = parkingBranchRepository.findById(request.getParkingBranchId())
                .orElseThrow(() -> new AuthenticationException("Không tìm thấy chi nhánh bãi xe"));

        User newStaff = new User();
        newStaff.setUserFullName(request.getUserFullName());
        newStaff.setUserEmail(request.getUserEmail());
        newStaff.setUserPhone(request.getUserPhone());
        newStaff.setUserPassword(passwordEncoder.encode(request.getUserPassword()));
        newStaff.setUserAddress(request.getUserAddress() != null ? request.getUserAddress() : "System");
        newStaff.setUserRole(UserRole.STAFF);
        newStaff.setParkingBranch(branch);
        
        User savedStaff = userRepository.save(newStaff);
        return convertToResponse(savedStaff);
    }

    /**
     * Tạo tài khoản quản lý (Manager) và gán cho một chi nhánh (Branch) cụ thể.
     * Luồng xử lý:
     * 1. Kiểm tra trùng lặp email và số điện thoại.
     * 2. Xác thực sự tồn tại của chi nhánh thông qua ParkingBranchId.
     * 3. Tạo mới đối tượng User, gán dữ liệu từ request.
     * 4. Mã hóa mật khẩu, thiết lập quyền là MANAGER và gán vào chi nhánh tương ứng.
     * 5. Lưu bản ghi vào cơ sở dữ liệu và trả về kết quả.
     */
    public UserResponse createManager(ManagerCreateRequest request) {
        if (userRepository.existsByUserEmail(request.getUserEmail())) {
            throw new AuthenticationException("Email đã tồn tại");
        }
        if (userRepository.existsByUserPhone(request.getUserPhone())) {
            throw new AuthenticationException("Số điện thoại đã tồn tại");
        }
        
        ParkingBranch branch = parkingBranchRepository.findById(request.getParkingBranchId())
                .orElseThrow(() -> new AuthenticationException("Không tìm thấy chi nhánh bãi xe"));

        User newManager = new User();
        newManager.setUserFullName(request.getUserFullName());
        newManager.setUserEmail(request.getUserEmail());
        newManager.setUserPhone(request.getUserPhone());
        newManager.setUserPassword(passwordEncoder.encode(request.getUserPassword()));
        newManager.setUserAddress(request.getUserAddress() != null ? request.getUserAddress() : "System");
        newManager.setUserRole(UserRole.MANAGER);
        newManager.setParkingBranch(branch);
        
        User savedManager = userRepository.save(newManager);
        return convertToResponse(savedManager);
    }

    /**
     * Xử lý đăng nhập hệ thống.
     * Luồng xử lý:
     * 1. Tìm kiếm người dùng dựa trên Email hoặc Số điện thoại (Identifier).
     * 2. Kiểm tra tài khoản có bị khóa do vi phạm (quá 3 lần) hay bị khóa thủ công không.
     * 3. Nếu hợp lệ, sử dụng AuthenticationManager để xác thực thông tin với mật khẩu.
     * 4. Xác thực thành công: Lấy thông tin User, tạo token (JWT) và đính kèm vào response.
     * 5. Bắt và xử lý các ngoại lệ (bị khóa, vô hiệu hóa, sai tài khoản/mật khẩu).
     */
    public UserResponse login(LoginRequest loginRequest) {
        // Kiểm tra trực tiếp xem tài khoản có bị đình chỉ do vi phạm không
        User preCheckUser = userRepository.findByUserEmail(loginRequest.getIdentifier());
        if (preCheckUser == null) {
            preCheckUser = userRepository.findByUserPhone(loginRequest.getIdentifier());
        }
        if (preCheckUser != null && (preCheckUser.getViolationCount() >= 3 || preCheckUser.isLocked())) {
            throw new AuthenticationException("Tài khoản của bạn đã bị đình chỉ do vi phạm quy định đặt giữ chỗ quá 3 lần.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getIdentifier(),
                            loginRequest.getUserPassword()
                    )
            );

            User user = (User) authentication.getPrincipal();

            // Tạo mã OTP ngẫu nhiên 6 chữ số
            String randomOtp = String.format("%06d", new java.util.Random().nextInt(999999));
            
            // Lưu OTP vào bộ nhớ tạm
            otpCache.put(loginRequest.getIdentifier(), randomOtp);
            
            // In ra Console để dễ dàng lấy mã test (Vì chưa gắn chức năng gửi Email)
            System.out.println("=================================================");
            System.out.println("🔔 MÃ OTP CỦA TÀI KHOẢN " + loginRequest.getIdentifier() + " LÀ: " + randomOtp);
            System.out.println("=================================================");

            // Thay vì trả về token ngay, ta chỉ trả về thông tin cơ bản
            UserResponse userResponse = new UserResponse();
            userResponse.setUserEmail(user.getUserEmail());
            userResponse.setUserPhone(user.getUserPhone());
            return userResponse; // Token = null -> Yêu cầu OTP

        } catch (LockedException e) {
            throw new AuthenticationException("Tài khoản của bạn đã bị đình chỉ do vi phạm quy định đặt giữ chỗ quá 3 lần.");

        } catch (DisabledException e) {
            throw new AuthenticationException("Tài khoản đã bị vô hiệu hóa");

        } catch (BadCredentialsException e) {
            throw new AuthenticationException("Email hoặc mật khẩu không đúng");

        } catch (Exception e) {
            throw new AuthenticationException("Đăng nhập thất bại");
        }
    }

    /**
     * Tải thông tin người dùng (dùng cho Spring Security).
     * Luồng xử lý:
     * 1. Tìm kiếm bằng Email. Nếu không có, tìm kiếm bằng Số điện thoại.
     * 2. Nếu không tìm thấy trong cả 2 trường hợp, ném UsernameNotFoundException.
     * 3. Trả về đối tượng User (thỏa mãn UserDetails) cho hệ thống xác thực.
     */
    @Override
    public User loadUserByUsername(String identifier) {
        User user = userRepository.findByUserEmail(identifier);
        if (user == null) {
            user = userRepository.findByUserPhone(identifier);
        }
        if (user == null) {
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Không tìm thấy người dùng với thông tin định danh: " + identifier);
        }
        return user;
    }

    /**
     * Chuyển đổi dữ liệu từ Entity (User) sang DTO (UserResponse)
     * giúp ẩn đi các thông tin nhạy cảm như mật khẩu trước khi trả về client,
     * đồng thời đính kèm thông tin tên chi nhánh nếu có.
     */
    private UserResponse convertToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setUserFullName(user.getUserFullName());
        response.setUserEmail(user.getUserEmail());
        response.setUserPhone(user.getUserPhone());
        response.setUserAddress(user.getUserAddress());
        response.setUserRole(user.getUserRole().name());
        response.setDeleted(user.isDeleted());
        response.setViolationCount(user.getViolationCount());
        response.setLocked(user.isLocked());
        if (user.getParkingBranch() != null) {
            response.setParkingBranchId(user.getParkingBranch().getParkingBranchId());
            response.setParkingBranchName(user.getParkingBranch().getBranchName());
        }
        return response;
    }
    /**
     * Lấy danh sách toàn bộ người dùng.
     * Luồng xử lý:
     * 1. Lấy tất cả entity User từ DB.
     * 2. Sử dụng Java Stream API để chuyển đổi (map) từng User thành UserResponse.
     * 3. Trả về danh sách đã được format.
     */
    public List<UserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
                    .map(this::convertToResponse) // chuyển đổi từng User thành UserResponse
                    .collect(Collectors.toList()); // thu thập kết quả vào một List<UserResponse> và trả về
    }
    /**
     * Lấy thông tin chi tiết một người dùng cụ thể.
     * Luồng xử lý:
     * 1. Tìm người dùng theo ID, báo lỗi nếu không thấy.
     * 2. Kiểm tra nếu người dùng đã bị xóa (deleted=true) thì ném ra ngoại lệ.
     * 3. Trả về DTO thông tin người dùng.
     */
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> new AuthenticationException("Không tìm thấy người dùng"));

        if(user.isDeleted()) {
            throw new AuthenticationException("Tài khoản người dùng đã bị xóa");
        } else {
            return convertToResponse(user);
        }
    
    }

    /**
     * Cập nhật thông tin cá nhân của người dùng.
     * Luồng xử lý:
     * 1. Tìm kiếm người dùng bằng ID.
     * 2. Cập nhật từng trường thông tin (Họ tên, Email, Số điện thoại, Địa chỉ) 
     *    nếu dữ liệu được truyền lên hợp lệ (không rỗng hoặc null).
     * 3. Lưu thông tin mới xuống DB và trả về kết quả.
     */
    public UserResponse updateUser(Long userId, UpdateUserRequest userRequest) { // Tìm người dùng cần cập nhật theo ID, nếu không tìm thấy thì quăng ra lỗi (RuntimeException)
        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> new AuthenticationException("Không tìm thấy người dùng"));
                                 
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
    /**
     * Thay đổi mật khẩu người dùng.
     * Luồng xử lý:
     * 1. Lấy thông tin user bằng ID.
     * 2. Xác thực mật khẩu cũ bằng PasswordEncoder, ném lỗi nếu không khớp.
     * 3. Kiểm tra mật khẩu mới không được trùng mật khẩu cũ.
     * 4. Kiểm tra mật khẩu mới và mật khẩu xác nhận phải giống nhau.
     * 5. Mã hóa mật khẩu mới và lưu vào cơ sở dữ liệu.
     */
    public UserResponse updatePassword(Long userId, ChangePasswordRequest changePasswordRequest) {
        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> new AuthenticationException("Không tìm thấy người dùng"));
        // kiểm tra mật khẩu cũ có đúng không   
        if(!passwordEncoder.matches(changePasswordRequest.getOldPassword(), user.getUserPassword())) {
            throw new AuthenticationException("Mật khẩu cũ không chính xác");
        }
        // kiểm tra mật khẩu mới có trùng với mật khẩu cũ không
        if(passwordEncoder.matches(changePasswordRequest.getNewPassword(), user.getUserPassword())) {
            throw new AuthenticationException("Mật khẩu mới phải khác mật khẩu cũ");
        }
        // kiểm tra mật khẩu mới và xác nhận mật khẩu có khớp nhau không
        if(!changePasswordRequest.getNewPassword().equals(changePasswordRequest.getConfirmPassword())) {
            throw new AuthenticationException("Mật khẩu mới và xác nhận mật khẩu không khớp");
        }
        // mã hóa mật khẩu mới và lưu vào database
        user.setUserPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        User updatedUser = userRepository.save(user);
        return convertToResponse(updatedUser);
    }

    /**
     * Đảo ngược trạng thái khóa (soft delete) của người dùng.
     * Luồng xử lý:
     * 1. Tìm người dùng bằng ID.
     * 2. Lấy trạng thái xóa (deleted) hiện tại và đảo ngược lại.
     * 3. Lưu lại và trả về kết quả.
     */
    public UserResponse deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> new AuthenticationException("Không tìm thấy người dùng"));
                                
       
         // tạm thời cho hàm này để chuyển đổi giữa tài khoảng xoá hay chưa xoá (demo)
        
        user.setDeleted(!user.isDeleted());
        
        userRepository.save(user);
        return convertToResponse(user);

    }



    /**
     * Đặt lại mật khẩu (quên mật khẩu).
     * Luồng xử lý:
     * 1. Tìm tài khoản bằng Email hoặc Số điện thoại.
     * 2. Báo lỗi nếu tài khoản không tồn tại hoặc đã bị xóa.
     * 3. Kiểm tra tính hợp lệ (mật khẩu mới và xác nhận phải khớp).
     * 4. Mã hóa mật khẩu mới và cập nhật vào hệ thống.
     */
    public UserResponse resetPassword(ResetPasswordRequest resetPasswordRequest) {
        String identifier = resetPasswordRequest.getEmailOrPhone();
        User user = userRepository.findByUserEmail(identifier);
        if (user == null) {
            user = userRepository.findByUserPhone(identifier);
        }
        if (user == null) {
            throw new AuthenticationException("Không tìm thấy tài khoản với Email hoặc Số điện thoại đã nhập");
        }
        if (user.isDeleted()) {
            throw new AuthenticationException("Tài khoản đã bị xóa hoặc vô hiệu hóa");
        }
        // kiểm tra mật khẩu mới và xác nhận mật khẩu có khớp nhau không
        if (!resetPasswordRequest.getNewPassword().equals(resetPasswordRequest.getConfirmPassword())) {
            throw new AuthenticationException("Mật khẩu mới và xác nhận mật khẩu không khớp");
        }
        // mã hóa mật khẩu mới và lưu vào database
        user.setUserPassword(passwordEncoder.encode(resetPasswordRequest.getNewPassword()));
        User updatedUser = userRepository.save(user);
        return convertToResponse(updatedUser);
    }

    /**
     * Xác thực mã OTP và cấp JWT Token
     */
    public UserResponse verifyOtp(VerifyOtpRequest request) {
        String identifier = request.getIdentifier();
        String otp = request.getOtp();
        
        String cachedOtp = otpCache.get(identifier);
        if (cachedOtp != null && cachedOtp.equals(otp)) {
            otpCache.remove(identifier);
            // Lấy thông tin user và tạo token
            User user = userRepository.findByUserEmail(identifier);
            if (user == null) {
                user = userRepository.findByUserPhone(identifier);
            }
            if (user == null) {
                throw new AuthenticationException("Không tìm thấy người dùng");
            }
            UserResponse userResponse = convertToResponse(user);
            String token = tokenService.generateToken(user);
            userResponse.setToken(token);
            return userResponse;
        } else {
            throw new AuthenticationException("Mã OTP không chính xác hoặc đã hết hạn");
        }
    }

}
