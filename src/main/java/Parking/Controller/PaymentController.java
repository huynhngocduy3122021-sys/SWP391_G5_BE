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
        String targetPath = "MONTHLY_TICKET".equals(response.getPaymentType()) ? "/user-dashboard" : "/payment-result";
        
        // Tạo URL trả về frontend kèm theo tham số kết quả
        String redirectUrl = frontendUrl + targetPath + "?success=" + response.isSuccess() 
                + "&message=" + URLEncoder.encode(response.getMessage() != null ? response.getMessage() : "", StandardCharsets.UTF_8);
                
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
