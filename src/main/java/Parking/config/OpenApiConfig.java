package Parking.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import org.springframework.context.annotation.Configuration;

/**
 * Lớp cấu hình cho Swagger UI (Tài liệu API tự động sinh ra bởi OpenAPI 3).
 * Giúp Frontend Developer có thể vào link /swagger-ui.html để đọc tài liệu và test API trực tiếp.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Parking Management API", // Tên của tài liệu API
                version = "1.0", // Phiên bản
                description = "API documentation for Parking Management System" // Mô tả ngắn gọn
        )
)
// Cấu hình nút "Authorize" trên Swagger UI để nhập Token (JWT)
@SecurityScheme(
        name = "api_key", // Tên scheme này sẽ được gọi lại ở các Controller (thông qua @SecurityRequirement)
        scheme = "bearer", // Định dạng token (Bearer Token)
        bearerFormat = "JWT", // Chuẩn token là JSON Web Token
        type = SecuritySchemeType.HTTP, // Loại bảo mật qua HTTP headers
        in = SecuritySchemeIn.HEADER // Token sẽ được gửi đính kèm trong Header của mỗi Request
)
public class OpenApiConfig {
}