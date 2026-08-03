package Parking.config;

import Parking.Model.User;
import Parking.Repository.UserRepository;
import Parking.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Component này có nhiệm vụ "gieo hạt" (seed) dữ liệu mẫu vào Database mỗi khi ứng dụng khởi động.
 * Vì nó implements CommandLineRunner, hàm run() sẽ tự động chạy ngay sau khi Spring Boot khởi động xong.
 */
@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Kiểm tra xem trong DB đã có tài khoản admin chưa (dựa vào email)
        if (!userRepository.existsByUserEmail("admin@gmail.com")) {
            // Nếu chưa có, tiến hành tạo một tài khoản ADMIN mặc định để có thể đăng nhập lần đầu
            User admin = new User();
            admin.setUserFullName("Admin");
            admin.setUserEmail("admin@gmail.com");
            admin.setUserPhone("0904563211");
            
            // Mật khẩu "123456" phải được mã hóa (hash) trước khi lưu vào DB để bảo mật
            admin.setUserPassword(passwordEncoder.encode("123456"));
            
            // Cấp quyền cao nhất (ADMIN)
            admin.setUserRole(UserRole.ADMIN);
            admin.setUserAddress("System");
            
            // Lưu xuống Database
            userRepository.save(admin);
            System.out.println("Seeded admin account: admin@gmail.com / 123456");
        }
    }
}
