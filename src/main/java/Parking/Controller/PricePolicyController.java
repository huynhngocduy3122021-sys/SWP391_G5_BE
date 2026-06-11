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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/price-policies")
@RequiredArgsConstructor
@CrossOrigin("*")
public class PricePolicyController {
    private final PricePolicyService pricePolicyService;

    @PostMapping
    public ResponseEntity<PricePolicy> createPricePolicy(
            @Valid @RequestBody CreatePricePolicyRequest request
    ) {
        return ResponseEntity.ok(pricePolicyService.createPricePolicy(request));
    }

    @GetMapping
    public ResponseEntity<List<PricePolicy>> getAllPricePolicies() {
        return ResponseEntity.ok(pricePolicyService.getAllPricePolicies());
    }
}
