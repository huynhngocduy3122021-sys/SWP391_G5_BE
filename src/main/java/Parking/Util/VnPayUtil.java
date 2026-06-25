package Parking.Util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VnPayUtil {

    private VnPayUtil() {
    }

    /**
     * Sắp xếp tham số theo tên key tăng dần,
     * loại bỏ giá trị null/rỗng và tạo query string.
     */
    public static String buildQueryString(
            Map<String, String> source
    ) {
        if (source == null || source.isEmpty()) {
            return "";
        }

        Map<String, String> sortedData =
                new TreeMap<>(source);

        StringBuilder queryString =
                new StringBuilder();

        for (Map.Entry<String, String> entry : sortedData.entrySet()) {

            String key = entry.getKey();
            String value = entry.getValue();

            if (key == null || key.isBlank() || value == null|| value.isBlank()) {

                continue;
            }

            if (queryString.length() > 0) {
                queryString.append("&");
            }

            queryString
                    .append(urlEncode(key))
                    .append("=")
                    .append(urlEncode(value));
        }

        return queryString.toString();
    }

    /**
     * Tạo chữ ký HMAC SHA-512.
     */
    public static String hmacSHA512(String secretKey,String data) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("VNPAY secret key cannot be empty");
        }

        if (data == null) {
            throw new IllegalArgumentException("VNPAY hash data cannot be null");
        }

        try {
            Mac hmac = Mac.getInstance("HmacSHA512");

            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8),"HmacSHA512");

            hmac.init(keySpec);

            byte[] hashBytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hashResult = new StringBuilder();

            for (byte hashByte : hashBytes) {
                hashResult.append(String.format( "%02x",hashByte ));
            }

            return hashResult.toString();

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot create VNPAY signature", exception);
        }
    }

    /**
     * So sánh hai chữ ký an toàn.
     */
    public static boolean secureHashEquals(String expectedHash,String receivedHash) {
        if (expectedHash == null || receivedHash == null) {

            return false;
        }

        byte[] expectedBytes = expectedHash.toLowerCase().getBytes(StandardCharsets.UTF_8);

        byte[] receivedBytes = receivedHash.toLowerCase().getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expectedBytes,receivedBytes);
    }

    /**
     * Mã hóa key và value để đặt vào URL.
     */
    private static String urlEncode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }
}