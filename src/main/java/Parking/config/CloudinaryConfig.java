package Parking.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

/**
 * Lớp cấu hình (Configuration) dùng để cài đặt kết nối với dịch vụ lưu trữ ảnh/video đám mây Cloudinary.
 * Spring Boot sẽ tự động quét và áp dụng cấu hình này khi ứng dụng khởi động nhờ @Configuration.
 */
@Configuration
public class CloudinaryConfig {
    
    // Đọc giá trị "cloudinary.cloud-name" từ file cấu hình application.properties (hoặc application.yml)
    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    // Đọc giá trị API Key từ file cấu hình
    @Value("${cloudinary.api-key}")
    private String apiKey;

    // Đọc giá trị API Secret (chìa khóa bí mật) từ file cấu hình
    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    /**
     * Khởi tạo đối tượng Cloudinary dưới dạng một Bean.
     * @Bean giúp Spring quản lý đối tượng này, từ đó các lớp khác (như Service) có thể gọi (Inject) 
     * nó vào để sử dụng (ví dụ: dùng để upload ảnh lên Cloudinary).
     */
    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(
            ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true // Bật HTTPS cho các đường dẫn ảnh trả về để bảo mật
            )
        );
    }
}
