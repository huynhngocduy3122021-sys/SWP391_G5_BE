package Parking.config;

import java.util.Locale;

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

    @Value("${app.backend.url:http://localhost:8081}")
    private String backendBaseUrl;

    public boolean isConfigured() {
        return enabled
                && StringUtils.hasText(tmnCode)
                && StringUtils.hasText(hashSecret)
                && StringUtils.hasText(payUrl)
                && StringUtils.hasText(getReturnUrl());
    }

    public String getReturnUrl() {
        return resolveBackendUrl(this.returnUrl, "/api/payments/vnpay-return");
    }

    public String getIpnUrl() {
        return resolveBackendUrl(this.ipnUrl, "/api/payments/vnpay-ipn");
    }

    private String resolveBackendUrl(String configuredUrl, String path) {
        String trimmed = configuredUrl == null ? null : configuredUrl.trim();
        if (!StringUtils.hasText(trimmed) || isFrontendRedirect(trimmed)) {
            return ensurePath(backendBaseUrl, path);
        }
        return trimmed;
    }

    private boolean isFrontendRedirect(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("localhost:5173")
                || lower.contains("5173")
                || lower.contains("/staff/exit")
                || lower.contains("/user-dashboard")
                || lower.contains("/payment-result")
                || lower.contains("localhost:3000");
    }

    private String ensurePath(String baseUrl, String path) {
        String normalizedBase = StringUtils.trimWhitespace(baseUrl);
        if (!StringUtils.hasText(normalizedBase)) {
            normalizedBase = "http://localhost:8081";
        }
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return normalizedBase + path;
    }
}
