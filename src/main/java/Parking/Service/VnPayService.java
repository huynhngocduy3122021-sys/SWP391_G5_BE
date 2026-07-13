package Parking.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import Parking.Model.Payment;
import Parking.Util.VnPayUtil;
import Parking.config.VnPayConfig;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VnPayService {
    // vnpay yeu cau thoi gian
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final VnPayConfig vnPayConfig;

    // kiem tra backend du cau hinh chua
    public boolean isAvailable() {
        return vnPayConfig.isConfigured();
    }
    // ta URL thanh toans VNpay
     public String createPaymentUrl(
            Payment payment,
            String clientIp
    ) {
        validateConfiguration();
        validatePayment(payment);

        LocalDateTime createdAt =
                LocalDateTime.now(VIETNAM_ZONE);

        LocalDateTime expiresAt =
                payment.getPaymentExpiresAt();

        /*
         * Nếu PaymentService chưa đặt thời gian hết hạn
         * thì mặc định cho phép thanh toán trong 15 phút.
         */
        if (expiresAt == null) {
            expiresAt = createdAt.plusMinutes(15);
            payment.setPaymentExpiresAt(expiresAt);
        }

        Map<String, String> params =
                new HashMap<>();

        params.put(
                "vnp_Version",
                "2.1.0"
        );

        params.put(
                "vnp_Command",
                "pay"
        );

        params.put(
                "vnp_TmnCode",
                vnPayConfig.getTmnCode()
        );

        /*
         * VNPAY yêu cầu amount nhân 100.
         *
         * Ví dụ:
         * 10.000 VND → 1.000.000
         */
        params.put(
                "vnp_Amount",
                convertAmount(payment.getAmount())
        );

        params.put(
                "vnp_CurrCode",
                "VND"
        );

        params.put(
                "vnp_TxnRef",
                payment.getTransactionRef()
        );

        String orderInfo = payment.getParkingSession() != null
                ? "Thanh toan phi gui xe session " + payment.getParkingSession().getParkingSessionId()
                : "Thanh toan dang ky the thang " + payment.getMonthlyTicketRequest().getId();
        params.put(
                "vnp_OrderInfo",
                orderInfo
        );

        params.put(
                "vnp_OrderType",
                "other"
        );

        params.put(
                "vnp_Locale",
                "vn"
        );

        params.put(
                "vnp_ReturnUrl",
                vnPayConfig.getReturnUrl()
        );

        params.put(
                "vnp_IpAddr",
                normalizeIpAddress(clientIp)
        );

        params.put(
                "vnp_CreateDate",
                createdAt.format(VNPAY_DATE_FORMAT)
        );

        params.put(
                "vnp_ExpireDate",
                expiresAt.format(VNPAY_DATE_FORMAT)
        );

        /*
         * Không thêm vnp_BankCode.
         *
         * Khi không có vnp_BankCode,
         * người dùng được chọn phương thức thanh toán
         * trên giao diện VNPAY.
         */

        /*
         * VnPayUtil sẽ:
         * 1. Sắp xếp tên tham số tăng dần.
         * 2. URL encode key và value.
         * 3. Ghép thành query string.
         */
        String queryString =
                VnPayUtil.buildQueryString(params);

        /*
         * Ký toàn bộ query string bằng HashSecret.
         */
        String secureHash =
                VnPayUtil.hmacSHA512(
                        vnPayConfig.getHashSecret(),
                        queryString
                );

        return vnPayConfig.getPayUrl()
                + "?"
                + queryString
                + "&vnp_SecureHash="
                + secureHash;
    }

    /**
     * Kiểm tra chữ ký Return URL hoặc IPN.
     */
    public boolean verifySignature(
            Map<String, String> originalParams
    ) {
        if (!vnPayConfig.isConfigured()) {
            return false;
        }

        if (originalParams == null
                || originalParams.isEmpty()) {
            return false;
        }

        String receivedSecureHash =
                originalParams.get("vnp_SecureHash");

        if (receivedSecureHash == null
                || receivedSecureHash.isBlank()) {
            return false;
        }

        /*
         * Tạo bản sao để không làm thay đổi
         * Map nhận từ Controller.
         */
        Map<String, String> paramsToVerify =
                new HashMap<>();

        for (Map.Entry<String, String> entry
                : originalParams.entrySet()) {

            String key = entry.getKey();
            String value = entry.getValue();

            if (key == null
                    || !key.startsWith("vnp_")) {
                continue;
            }

            /*
             * Hai trường này không tham gia
             * vào dữ liệu dùng để kiểm tra checksum.
             */
            if ("vnp_SecureHash".equals(key)
                    || "vnp_SecureHashType".equals(key)) {
                continue;
            }

            if (value == null || value.isBlank()) {
                continue;
            }

            paramsToVerify.put(key, value);
        }

        String hashData =
                VnPayUtil.buildQueryString(
                        paramsToVerify
                );

        String expectedSecureHash =
                VnPayUtil.hmacSHA512(
                        vnPayConfig.getHashSecret(),
                        hashData
                );

        return VnPayUtil.secureHashEquals(
                expectedSecureHash,
                receivedSecureHash
        );
    }

    /**
     * Kiểm tra callback có đúng Merchant của mình không.
     */
    public boolean isCorrectTmnCode(
            Map<String, String> params
    ) {
        if (!vnPayConfig.isConfigured()
                || params == null) {
            return false;
        }

        String receivedTmnCode =
                params.get("vnp_TmnCode");

        if (receivedTmnCode == null) {
            return false;
        }

        return vnPayConfig
                .getTmnCode()
                .equals(receivedTmnCode);
    }

    /**
     * Chuyển amount từ VND sang định dạng VNPAY.
     *
     * 10.000 → 1.000.000
     */
    public String convertAmount(
            BigDecimal amount
    ) {
        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        return amount
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /**
     * Đổi amount VNPAY trả về thành VND.
     *
     * 1.000.000 → 10.000
     */
    public BigDecimal convertVnPayAmount(
            String vnPayAmount
    ) {
        if (vnPayAmount == null
                || vnPayAmount.isBlank()) {
            throw new IllegalArgumentException(
                    "VNPAY amount is required"
            );
        }

        try {
            return new BigDecimal(vnPayAmount)
                    .divide(
                            BigDecimal.valueOf(100),
                            0,
                            RoundingMode.UNNECESSARY
                    );

        } catch (ArithmeticException
                 | NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "Invalid VNPAY amount: "
                            + vnPayAmount,
                    exception
            );
        }
    }

    /**
     * Khi chạy local, request có thể trả IPv6 localhost.
     * VNPAY cần một địa chỉ IP hợp lệ.
     */
    private String normalizeIpAddress(
            String clientIp
    ) {
        if (clientIp == null
                || clientIp.isBlank()
                || "::1".equals(clientIp)
                || "0:0:0:0:0:0:0:1"
                    .equals(clientIp)) {
            return "127.0.0.1";
        }

        /*
         * X-Forwarded-For đôi khi có nhiều IP:
         *
         * 203.0.113.1, 10.0.0.1
         *
         * Chỉ lấy IP đầu tiên.
         */
        if (clientIp.contains(",")) {
            return clientIp
                    .split(",")[0]
                    .trim();
        }

        return clientIp.trim();
    }

    private void validateConfiguration() {
        if (!vnPayConfig.isConfigured()) {
            throw new IllegalStateException(
                    "VNPAY Merchant Sandbox "
                    + "chưa được cấu hình đầy đủ"
            );
        }
    }

    private void validatePayment(
            Payment payment
    ) {
        if (payment == null) {
            throw new IllegalArgumentException(
                    "Payment cannot be null"
            );
        }

        if (payment.getAmount() == null
                || payment.getAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        if (payment.getTransactionRef() == null
                || payment.getTransactionRef()
                .isBlank()) {
            throw new IllegalArgumentException(
                    "Payment transactionRef is required"
            );
        }

        if (payment.getParkingSession() == null && payment.getMonthlyTicketRequest() == null) {
            throw new IllegalArgumentException(
                    "Payment parkingSession or monthlyTicketRequest is required"
            );
        }

        if (payment.getParkingSession() != null && payment
                .getParkingSession()
                .getParkingSessionId() == null) {
            throw new IllegalArgumentException(
                    "Parking session must be saved "
                    + "before creating VNPAY URL"
            );
        }

        if (payment.getMonthlyTicketRequest() != null && payment
                .getMonthlyTicketRequest()
                .getId() == null) {
            throw new IllegalArgumentException(
                    "Monthly ticket request must be saved "
                    + "before creating VNPAY URL"
            );
        }
    }
}
