package Parking.config;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.servlet.HandlerExceptionResolver;
import Parking.Service.TokenService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import Parking.Model.User;
import java.util.List;
import java.io.IOException;
import Parking.exception.exceptions.AuthenticationException;

/**
 * Lớp Filter (Bộ lọc) đứng ở cửa ngõ của ứng dụng.
 * Mọi request từ Frontend (React/Vue/Postman) gửi xuống BE đều phải đi qua cái cửa này đầu tiên.
 * Chức năng chính: Kiểm tra xem Request đó có mang theo Token hợp lệ không.
 */
@Component
public class filter extends OncePerRequestFilter {
    
    // Trình xử lý ngoại lệ (lỗi) để gửi thông báo lỗi đẹp mắt về cho Frontend nếu token sai
    @Autowired
    @Qualifier("handlerExceptionResolver")
    private HandlerExceptionResolver resolver;

    // Service dùng để giải mã và kiểm tra Token
    @Autowired
    private TokenService tokenService;

    // Danh sách các API công khai (Không cần đăng nhập vẫn gọi được)
    private final List<String> PUBLIC_API_ENDPOINTS = List.of(
               "/",
                "/index.html",
                "/favicon.ico",
                "/style.css",
                "/app.js",
                "/api/auth/register", // Ai cũng đăng ký được
                "/api/auth/verify-register-otp", // Xác thực OTP không cần token
                "/api/auth/login", // Chưa đăng nhập thì mới cần gọi login
                "/error",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/swagger-ui/index.html",
                "/v3/api-docs/**",
                "/v3/api-docs",
                "/api/auth/reset-password",
                "/api/payments/vnpay-return", // Call back từ VNPay
                "/api/payments/vnpay-ipn"
    );

    // Danh sách các API lấy dữ liệu công khai (Chỉ hỗ trợ phương thức GET)
    private final List<String> PUBLIC_GET_ENDPOINTS = List.of(
                "/api/parking-branches", // Xem danh sách chi nhánh
                "/api/parking-zones/**", // Xem khu vực
                "/api/vehicle-types", // Xem loại xe
                "/api/price-policies" // Xem bảng giá
    );

            /**
             * Hàm kiểm tra xem đường dẫn hiện tại có nằm trong danh sách Public hay không.
             */
            public boolean isPublicAPI(String uri, String method) {
                AntPathMatcher matcher = new AntPathMatcher();

                boolean isPublic = PUBLIC_API_ENDPOINTS.stream()
                        .anyMatch(pattern -> matcher.match(pattern, uri));
                
                if (isPublic) return true;

                if ("GET".equalsIgnoreCase(method)) {
                    return PUBLIC_GET_ENDPOINTS.stream()
                            .anyMatch(pattern -> matcher.match(pattern, uri));
                }

                return false;
            }
            
    /**
     * Hàm lõi xử lý việc lọc request.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
         HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // Cho phép các request dạng OPTIONS (thường dùng để trình duyệt check CORS trước) đi qua
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getServletPath();
      

        // Bỏ qua WebSocket handshake (dùng cho chat realtime hoặc thông báo)
        if (uri.startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Nếu là API công khai (Public) -> Cho đi tiếp vào Controller luôn
        if(isPublicAPI(uri, request.getMethod())){
            //api public
            // tất cả access
            filterChain.doFilter(request , response);

        } else {
            // Nếu là API cần bảo mật (Private)
            // Bắt buộc phải có token trong Header
            String token = getToken(request);
            if(token == null) {
                // Không có token -> Báo lỗi 401
                resolver.resolveException(request , response , null , new AuthenticationException("Token không được để trống!"));
                return;
            }
            
            // Nếu có token -> Tiến hành giải mã và xác minh (verify)
            User member = null;
            try {
                // Giải mã Token để lấy ra thông tin người dùng
                member = tokenService.extractToken(token);
                if (member == null) {
                    resolver.resolveException(request , response , null , new AuthenticationException("Không tìm thấy người dùng!"));
                    return;
                }
                
                // Cấm cửa nếu tài khoản đang bị khóa (VD: do spam đặt chỗ)
                if (!member.isAccountNonLocked()) {
                    resolver.resolveException(request , response , null , new AuthenticationException("Tài khoản của bạn đã bị đình chỉ do vi phạm quy định đặt giữ chỗ quá 3 lần."));
                    return;
                }
            } catch (ExpiredJwtException expiredJwtException) {
                // Lỗi 1: Token đã hết hạn thời gian sử dụng
                resolver.resolveException(request , response , null , new AuthenticationException("Token đã hết hạn!"));
                return;
            } catch (MalformedJwtException malformedJwtException) {
                // Lỗi 2: Token bị sai định dạng (có thể do hacker sửa đổi)
                resolver.resolveException(request, response, null, new AuthenticationException("Token không hợp lệ!"));
                return;
            } catch (Exception exception) {
                resolver.resolveException(request, response, null, new AuthenticationException("Xác thực thất bại: " + exception.getMessage()));
                return;
            }
            
            // Nếu mọi thứ đều OK (Token hợp lệ, chưa hết hạn, tài khoản không bị khóa)
            // Lưu thông tin người dùng này vào SecurityContextHolder của Spring Security
            // Từ đây về sau, ở bất kỳ Controller nào cũng có thể gọi SecurityContextHolder để biết ai đang gọi API
            UsernamePasswordAuthenticationToken
                    authenToken =
                    new UsernamePasswordAuthenticationToken(member, token, member.getAuthorities());
            authenToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenToken);

            // Cho phép đi tiếp vào các tầng sâu hơn (Controller)
            filterChain.doFilter(request , response);

        }


    }

    /**
     * Hàm bóc tách chữ "Bearer " ra khỏi chuỗi Header để lấy đoạn Token chuẩn.
     */
    public String getToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring(7); // Cắt bỏ 7 ký tự đầu "Bearer "
    }
}
