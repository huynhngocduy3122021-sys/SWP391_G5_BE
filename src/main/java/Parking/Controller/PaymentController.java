package Parking.Controller;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
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
import Parking.web.PaymentRedirectUrlBuilder;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@SecurityRequirement(name = "api_key")
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin("*")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRedirectUrlBuilder paymentRedirectUrlBuilder;

    @GetMapping("/vnpay-return")
    @Operation(summary = "VnPay Redirect Return URL Callback")
    public ResponseEntity<?> vnPayReturn(
            @RequestParam Map<String, String> params,
            @RequestHeader(value = "Accept", defaultValue = "") String acceptHeader
    ) {
        VnpayReturnResponse response = paymentService.handleVnPayCallback(params);
        
        boolean isAjax = acceptHeader.contains("application/json") || 
                         acceptHeader.contains("application/javascript") ||
                         (acceptHeader.contains("*/*") && !acceptHeader.contains("text/html"));

        if (isAjax) {
            return ResponseEntity.ok(response);
        }
        
        URI redirectUri = paymentRedirectUrlBuilder.buildRedirectUri(response, params);
                
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
    }

    @GetMapping("/vnpay-ipn")
    @Operation(summary = "VnPay IPN Backend Callback")
    public ResponseEntity<Map<String, String>> vnPayIpn(@RequestParam Map<String, String> params) {
        Map<String, String> response = paymentService.handleVnPayIpn(params);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    @Operation(summary = "Lấy tất cả payments cho báo cáo doanh thu")
    public ResponseEntity<?> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPaymentsForReport());
    }
}
