package com.abhay.payment.api;

import com.abhay.payment.infrastructure.reconciliation.ReconciliationService;
import com.abhay.payment.infrastructure.security.MerchantPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {
    public record SettlementRequest(UUID transactionId,
            @NotBlank @Size(max = 160) String providerReference,
            @DecimalMin(value = "0.0001") BigDecimal settledAmount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotBlank String providerStatus, Instant settledAt) { }
    private final ReconciliationService reconciliation;
    public ReconciliationController(ReconciliationService reconciliation) { this.reconciliation = reconciliation; }

    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> ingest(@Valid @RequestBody SettlementRequest request,
            @AuthenticationPrincipal MerchantPrincipal principal) {
        return reconciliation.ingest(principal.merchantId(),
                new ReconciliationService.SettlementInput(request.transactionId(), request.providerReference(),
                        request.settledAmount(), request.currency(), request.providerStatus(), request.settledAt()));
    }

    @GetMapping("/exceptions")
    public Object exceptions(@AuthenticationPrincipal MerchantPrincipal principal) {
        return reconciliation.exceptions(principal.merchantId());
    }
}
