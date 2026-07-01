package Parking.Service;

import Parking.Model.User;
import Parking.exception.exceptions.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AuthenticationException("Yêu cầu đăng nhập");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User)) {
            throw new AuthenticationException("Thông tin đăng nhập không hợp lệ");
        }

        return (User) principal;
    }
}
