package Parking.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

// Đánh dấu đây là một Thực thể (Entity) để Spring Data JPA tự động tạo bảng trong cơ sở dữ liệu
@Entity
// Tên bảng trong cơ sở dữ liệu SQL Server sẽ là 'slot'
@Table(name = "slot")
// Lombok tự động tạo Getter, Setter, toString, equals, hashCode cho các thuộc tính bên dưới
@Data
public class Slot {

    // Đánh dấu đây là thuộc tính Khóa chính (Primary Key)
    @Id
    // Giá trị khóa chính sẽ tự động tăng (IDENTITY) trong SQL Server khi lưu bản ghi mới
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Mã vị trí đỗ xe (Ví dụ: A1, A2, B3,...)
    private String slotCode;

    // Loại phương tiện được phép đỗ (Ví dụ: Xe máy, Ô tô,...)
    private String vehicleType;

    // Trạng thái của vị trí đỗ xe (mặc định ban đầu là true - còn trống / có sẵn)
    private boolean available = true;
}