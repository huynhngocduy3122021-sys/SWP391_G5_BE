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
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    @Lazy
    private UserService userService; // Sử dụng UserService làm UserDetailsService để lấy thông tin người dùng từ database
    @Autowired
    private filter filter;
  
    
    @Bean
    public PasswordEncoder passwordEncoder() { // mã hóa mật khẩu bằng BCrypt
        return new BCryptPasswordEncoder();
    }

     @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration  authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

  @Bean
    // chưa phân quyền cho member chỉ test code
    public SecurityFilterChain securityFilterChain(HttpSecurity http)throws Exception{

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                            req -> req
                
                // Cho phép API đăng ký / đăng nhập
                .requestMatchers("/", "/index.html", "/favicon.ico", "/style.css", "/app.js", "/api/auth/register","/api/auth/login","/api/auth/reset-password", "/error").permitAll()

                // Cho phép Swagger chạy không cần token
                .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger-ui/index.html",
                        "/v3/api-docs/**",
                        "/v3/api-docs",
                        "/api/parking-sessions/**",
                        "/api/vehicle-types/**",
                        "/api/parking-cards/**",
                        "/api/parking-zones/**",
                        "/api/price-policies/**",
                        "/api/parking-session/**",
                        "/api/parking-branches/**",
                        "/api/parking-floors/**",
                        "/api/payments/**"
                        
                ).permitAll()

                // Các API còn lại bắt buộc phải đăng nhập
                .anyRequest().authenticated()

                )
                .userDetailsService(userService) // Sử dụng UserService để xác thực người dùng
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
