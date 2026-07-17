package Parking.config;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.Getter;

@Component
@Getter
public class VnPayConfig {
    @Value("${vnpay.enabled:false}")
    private boolean enabled;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${vnpay.tmn-code:}")
    private String tmnCode;

    @Value("${vnpay.hash-secret:}")
    private String hashSecret;

    @Value("${vnpay.pay-url}")
    private String payUrl;

    @Value("${vnpay.return-url}")
    private String returnUrl;

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
