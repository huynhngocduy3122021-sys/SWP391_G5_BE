package Parking.Controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import Parking.Service.PaymentService;
import Parking.dto.response.VnpayReturnResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@SecurityRequirement(name = "api_key")
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin("*")
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @GetMapping("/vnpay-return")
    @Operation(summary = "VnPay Redirect Return URL Callback")
    public ResponseEntity<?> vnPayReturn(
            @RequestParam Map<String, String> params,
            @RequestHeader(value = "Accept", defaultValue = "") String acceptHeader
    ) {
        VnpayReturnResponse response = paymentService.handleVnPayCallback(params);
        
        // Phân biệt request từ Axios (frontend API call) và request từ Trình duyệt (redirect)
        boolean isAjax = acceptHeader.contains("application/json") || 
                         acceptHeader.contains("application/javascript") ||
                         (acceptHeader.contains("*/*") && !acceptHeader.contains("text/html"));

        if (isAjax) {
            return ResponseEntity.ok(response);
        }

        // Xác định trang đích dựa trên loại thanh toán
        // PARKING_SESSION: redirect về trang Staff Exit (truyền raw VNPay params để FE Staff tự xử lý)
        // MONTHLY_TICKET: redirect về trang Payment Result (truyền success/message cho User)
        String targetPath;
        String queryString;
        
        if ("PARKING_SESSION".equals(response.getPaymentType())) {
            // Staff checkout: redirect về /staff/exit kèm raw VNPay params để GateOutPanel xử lý
            targetPath = "/staff/exit";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                  .append("=")
                  .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
            queryString = sb.toString();
        } else {
            // Monthly ticket payment: redirect về /payment-result kèm thông tin kết quả
            targetPath = "/payment-result";
            queryString = "success=" + response.isSuccess()
                    + (response.getPaymentType() != null ? "&paymentType=" + response.getPaymentType() : "")
                    + (response.getTransactionRef() != null ? "&transactionRef=" + response.getTransactionRef() : "")
                    + (response.getRequestId() != null ? "&requestId=" + response.getRequestId() : "")
                    + "&message=" + URLEncoder.encode(response.getMessage() != null ? response.getMessage() : "", StandardCharsets.UTF_8);
        }
        
        // Chuẩn hóa frontendUrl để tránh dấu / kép
        String normalizedFrontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        
        String redirectUrl = normalizedFrontendUrl + targetPath + "?" + queryString;
                
        // Trả về HTTP 302 Redirect để trình duyệt tự động chuyển hướng về trang frontend
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    @GetMapping("/vnpay-ipn")
    @Operation(summary = "VnPay IPN Backend Callback")
    public ResponseEntity<Map<String, String>> vnPayIpn(@RequestParam Map<String, String> params) {
        Map<String, String> response = paymentService.handleVnPayIpn(params);
        return ResponseEntity.ok(response);
    }
}
