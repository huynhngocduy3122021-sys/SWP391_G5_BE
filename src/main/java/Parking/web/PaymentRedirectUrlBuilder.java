package Parking.web;

import Parking.dto.response.VnpayReturnResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class PaymentRedirectUrlBuilder {

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public URI buildRedirectUri(VnpayReturnResponse response) {
        String targetPath = "PARKING_SESSION".equals(response.getPaymentType())
                ? "/staff/exit"
                : "/payment-result";

        String query = "success=" + response.isSuccess()
                + "&paymentType=" + encode(response.getPaymentType())
                + "&transactionRef=" + encode(response.getTransactionRef())
                + "&responseCode=" + encode(response.getResponseCode())
                + "&message=" + encode(response.getMessage());

        if (response.getRequestId() != null) {
            query += "&requestId=" + response.getRequestId();
        }

        String baseUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;

        return URI.create(baseUrl + targetPath + "?" + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
