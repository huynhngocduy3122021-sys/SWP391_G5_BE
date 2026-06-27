package Parking.config;

import Parking.Model.User;
import Parking.Repository.UserRepository;
import Parking.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (!userRepository.existsByUserEmail("admin@gmail.com")) {
            User admin = new User();
            admin.setUserFullName("Admin");
            admin.setUserEmail("admin@gmail.com");
            admin.setUserPhone("0904563211");
            admin.setUserPassword(passwordEncoder.encode("123456"));
            admin.setUserRole(UserRole.ADMIN);
            admin.setUserAddress("System");
            userRepository.save(admin);
            System.out.println("Seeded admin account: admin@gmail.com / 123456");
        }
    }
}
