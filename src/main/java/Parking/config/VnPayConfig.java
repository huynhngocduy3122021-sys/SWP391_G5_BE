package Parking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.springframework.util.StringUtils;
import lombok.Getter;

/**
 * Lớp cấu hình dùng để đọc các thông số thiết lập của cổng thanh toán VNPay.
 * Dữ liệu được đọc trực tiếp từ file application.properties (hoặc application.yml).
 */
@Component
@Getter // Lombok tự động tạo các hàm Getter cho toàn bộ biến bên dưới
public class VnPayConfig {
    
    // Công tắc Bật/Tắt tính năng VNPay (Nếu trong file config không ghi gì thì mặc định là false)
    @Value("${vnpay.enabled:false}")
    private boolean enabled;

    // Mã website (Terminal ID) do VNPay cấp cho dự án của bạn
    @Value("${vnpay.tmn-code:}")
    private String tmnCode;

    // Chuỗi mã bảo mật (Secret Key) do VNPay cấp để tạo chữ ký điện tử chống giả mạo dữ liệu
    @Value("${vnpay.hash-secret:}")
    private String hashSecret;

    // Đường link API để chuyển hướng người dùng sang trang thanh toán của VNPay
    @Value("${vnpay.pay-url}")
    private String payUrl;

    // Đường link mà VNPay sẽ tự động gọi trở lại Frontend sau khi khách thanh toán xong (Return URL)
    @Value("${vnpay.return-url}")
    private String returnUrl;

    // Đường link bí mật mà hệ thống server VNPay gọi ngầm về Backend để chốt trạng thái đơn hàng (IPN URL)
    @Value("${vnpay.ipn-url:}")
    private String ipnUrl;

    /**
     * Kiểm tra xem các cấu hình VNPay đã được nhập đầy đủ trong file application.properties hay chưa.
     */
    public boolean isConfigured() {
        return enabled
                && StringUtils.hasText(tmnCode)
                && StringUtils.hasText(hashSecret)
                && StringUtils.hasText(payUrl)
                && StringUtils.hasText(returnUrl);
    }
}
