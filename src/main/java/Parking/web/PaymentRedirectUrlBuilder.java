package Parking.web;

import Parking.dto.response.VnpayReturnResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class PaymentRedirectUrlBuilder {

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public URI buildRedirectUri(VnpayReturnResponse response, Map<String, String> rawParams) {
        String targetPath;
        String queryString;

        if ("PARKING_SESSION".equals(response.getPaymentType())) {
            targetPath = "/staff/exit";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : rawParams.entrySet()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                  .append("=")
                  .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
            queryString = sb.toString();
        } else {
            targetPath = "/user-dashboard";
            
            String extraParams = "";
            if (response.getLicensePlate() != null) {
                extraParams += "&licensePlate=" + URLEncoder.encode(response.getLicensePlate(), StandardCharsets.UTF_8);
            }
            if (response.getVehicleId() != null) {
                extraParams += "&vehicleId=" + response.getVehicleId();
            }
            if (response.getPolicyName() != null) {
                extraParams += "&policyName=" + URLEncoder.encode(response.getPolicyName(), StandardCharsets.UTF_8);
            }
            if (response.getPolicyId() != null) {
                extraParams += "&policyId=" + response.getPolicyId();
            }

            queryString = "success=" + response.isSuccess()
                    + (response.getPaymentType() != null ? "&paymentType=" + response.getPaymentType() : "")
                    + (response.getTransactionRef() != null ? "&transactionRef=" + response.getTransactionRef() : "")
                    + (response.getRequestId() != null ? "&requestId=" + response.getRequestId() : "")
                    + "&message=" + URLEncoder.encode(response.getMessage() != null ? response.getMessage() : "", StandardCharsets.UTF_8)
                    + extraParams;
        }

        String normalizedFrontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        
        String redirectUrl = normalizedFrontendUrl + targetPath + "?" + queryString;
        return URI.create(redirectUrl);
    }
}
