package Parking.Controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Parking.Model.PricePolicy;
import Parking.Service.PricePolicyService;
import Parking.dto.request.CreatePricePolicyRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/price-policies")
@RequiredArgsConstructor
@CrossOrigin("*")
public class PricePolicyController {
    private final PricePolicyService pricePolicyService;

    @PostMapping
    @Operation(summary = "Hàm tạo đơn giá của xe")
    public ResponseEntity<PricePolicy> createPricePolicy(
            @Valid @RequestBody CreatePricePolicyRequest request
    ) {
        return ResponseEntity.ok(pricePolicyService.createPricePolicy(request));
    }

    @GetMapping
    @Operation(summary = "Hàm lấy những giá giữ xe")
    public ResponseEntity<List<PricePolicy>> getAllPricePolicies() {
        return ResponseEntity.ok(pricePolicyService.getAllPricePolicies());
    }
}
