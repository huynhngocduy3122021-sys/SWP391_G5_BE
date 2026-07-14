package Parking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import Parking.Service.UserService;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
/**
 * Lớp cấu hình bảo mật chính của ứng dụng (Trái tim của Spring Security).
 * Quản lý việc ai có quyền truy cập vào đường dẫn nào, và cấu hình các filter bảo mật.
 */
@Configuration
@EnableMethodSecurity // Cho phép dùng @PreAuthorize("hasRole('ADMIN')") trên các hàm Controller
public class SecurityConfig {

    @Autowired
    @Lazy // @Lazy giúp tránh lỗi vòng lặp phụ thuộc (Circular Dependency) khi Inject UserService
    private UserService userService; // Sử dụng UserService để lấy thông tin người dùng từ Database xác thực

    @Autowired
    private filter filter; // Gọi lớp JWT Filter (tạo ở file filter.java) vào đây
  
    /**
     * Đăng ký bộ mã hóa mật khẩu.
     * Dùng BCrypt - một thuật toán mã hóa một chiều rất mạnh và an toàn.
     */
    @Bean
    public PasswordEncoder passwordEncoder() { 
        return new BCryptPasswordEncoder();
    }

    /**
     * Quản lý xác thực chung của hệ thống (Dùng khi xử lý hàm đăng nhập).
     */
     @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration  authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Xây dựng chuỗi lọc bảo mật (Security Filter Chain).
     * Đây là nơi quy định API nào mở, API nào khóa.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)throws Exception{

        return http
                .csrf(AbstractHttpConfigurer::disable) // Tắt CSRF (Vì API dùng JWT token thay vì Session/Cookie nên không lo lỗi này)
                .authorizeHttpRequests(
                            req -> req
                
                // 1. Cho phép API đăng ký / đăng nhập và các file tĩnh được truy cập tự do (permitAll)
                .requestMatchers("/", "/index.html", "/favicon.ico", "/style.css", "/app.js", "/api/auth/**", "/error").permitAll()
                
                // 2. Cho phép xem thông tin sơ đồ đỗ xe công cộng (không cần token) nhưng chỉ được dùng hàm GET
                .requestMatchers(org.springframework.http.HttpMethod.GET, 
                    "/api/parking/slots", 
                    "/api/parking-branches", 
                    "/api/parking-zones/**", 
                    "/api/vehicle-types", 
                    "/api/price-policies"
                ).permitAll()

                // 3. VNPay callback - là hệ thống VNPay gọi thẳng về server của mình, không mang JWT token, bắt buộc phải để public
                .requestMatchers("/api/payments/vnpay-return", "/api/payments/vnpay-ipn").permitAll()

                // 4. Cho phép giao diện tài liệu Swagger chạy không cần token
                .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger-ui/index.html",
                        "/v3/api-docs/**",
                        "/v3/api-docs"
                ).permitAll()

                // 5. Mọi API khác còn lại KHÔNG cấu hình ở trên thì bắt buộc phải được chứng thực (có Token hợp lệ)
                .anyRequest().authenticated()

                )
                .userDetailsService(userService) // Chỉ định Service dùng để load user
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Tắt Session (STATELESS), ép hệ thống chỉ dùng JWT
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build(); // Chèn cái JWT Filter của mình chạy TRƯỚC filter bảo mật mặc định của Spring
    }
}
