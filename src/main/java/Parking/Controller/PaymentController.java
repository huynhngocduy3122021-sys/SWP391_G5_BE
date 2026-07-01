package Parking.Controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import Parking.Service.PaymentService;
import Parking.dto.response.VnpayReturnResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@SecurityRequirement(name = "api_key")
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin("*")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/vnpay-return")
    @Operation(summary = "VnPay Redirect Return URL Callback")
    public ResponseEntity<VnpayReturnResponse> vnPayReturn(@RequestParam Map<String, String> params) {
        VnpayReturnResponse response = paymentService.handleVnPayCallback(params);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vnpay-ipn")
    @Operation(summary = "VnPay IPN Backend Callback")
    public ResponseEntity<Map<String, String>> vnPayIpn(@RequestParam Map<String, String> params) {
        Map<String, String> response = paymentService.handleVnPayIpn(params);
        return ResponseEntity.ok(response);
    }
}
