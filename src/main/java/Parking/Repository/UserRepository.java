package Parking.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import Parking.model.User;


public interface UserRepository extends JpaRepository<User, Long> {
    // Tìm kiếm người dùng theo email
    User findByUserEmail(String userEmail);
    // Kiểm tra xem email đã tồn tại hay chưa
    boolean existsByUserEmail(String userEmail);
    // Tìm kiếm người dùng theo số điện thoại
    User findByUserPhone(String userPhone);
    // Kiểm tra xem số điện thoại đã tồn tại hay chưa
    boolean existsByUserPhone(String userPhone);
    
    
}
