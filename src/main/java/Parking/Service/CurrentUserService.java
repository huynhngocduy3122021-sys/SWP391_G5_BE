package Parking.Service;

import Parking.Model.User;
import Parking.exception.exceptions.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    /**
     * Lấy thông tin người dùng hiện đang đăng nhập từ Spring Security Context.
     *
     * @return đối tượng User của người dùng đã đăng nhập
     * @throws AuthenticationException nếu chưa đăng nhập hoặc thông tin đăng nhập
     *                                 không phải là một đối tượng User hợp lệ
     */
    public User getCurrentUser() {
        // SecurityContext lưu thông tin xác thực của request đang được xử lý.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Từ chối khi không có thông tin xác thực, chưa được xác thực,
        // hoặc Spring Security chỉ tạo người dùng ẩn danh (anonymousUser).
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AuthenticationException("Yêu cầu đăng nhập");
        }

        // Principal là thông tin đại diện cho người dùng đã được xác thực.
        Object principal = authentication.getPrincipal();

        // Hệ thống yêu cầu principal phải là entity User; kiểu khác cho thấy
        // cấu hình xác thực hoặc dữ liệu đăng nhập không đúng như mong đợi.
        if (!(principal instanceof User)) {
            throw new AuthenticationException("Thông tin đăng nhập không hợp lệ");
        }

        // Sau khi kiểm tra kiểu an toàn, trả về người dùng hiện tại cho service gọi hàm.
        return (User) principal;
    }
}
