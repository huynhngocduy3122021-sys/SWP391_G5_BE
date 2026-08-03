package Parking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.modelmapper.ModelMapper;

/**
 * Lớp cấu hình (Configuration) dùng để khởi tạo thư viện ModelMapper.
 * Chức năng của ModelMapper là giúp tự động copy dữ liệu từ Object này sang Object khác
 * (Thường dùng để copy từ Model (Entity) sang DTO và ngược lại) thay vì phải viết các hàm Get/Set thủ công.
 */
@Configuration
public class ModelMapperConfig {
    
    /**
     * Tạo một Bean ModelMapper để các lớp khác (như Service) có thể Inject (tiêm) vào sử dụng.
     */
    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }
}
