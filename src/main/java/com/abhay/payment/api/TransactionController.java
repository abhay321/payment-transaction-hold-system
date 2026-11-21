package com.abhay.payment.api;

import com.abhay.payment.application.dto.TransactionSnapshot;
import com.abhay.payment.application.service.PaymentTransactionService;
import com.abhay.payment.domain.model.Money;
import com.abhay.payment.infrastructure.security.MerchantPrincipal;
import com.abhay.payment.infrastructure.security.SecurityAuditLogger;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final PaymentTransactionService service;
    private final SecurityAuditLogger audit;

    public TransactionController(PaymentTransactionService service, SecurityAuditLogger audit) {
        this.service = service;
        this.audit = audit;
    }

    public record CreateRequest(
            @DecimalMin(value = "0.0001") BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) { }

    public record AmountRequest(
            @DecimalMin(value = "0.0001") BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) { }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionSnapshot create(@Valid @RequestBody CreateRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200)
            @Pattern(regexp = "[A-Za-z0-9._:-]{1,200}") String key,
            @AuthenticationPrincipal MerchantPrincipal principal) {
        TransactionSnapshot result = service.createForMerchant(principal.merchantId(),
                Money.of(request.amount(), Currency.getInstance(request.currency())), key);
        audit.transactionAction("CREATE", principal, result.id());
        return result;
    }

    @PostMapping("/{id}/hold")
    public TransactionSnapshot hold(@PathVariable UUID id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200)
            @Pattern(regexp = "[A-Za-z0-9._:-]{1,200}") String key,
            @AuthenticationPrincipal MerchantPrincipal principal) {
        TransactionSnapshot result = service.holdForMerchant(principal.merchantId(), id, key);
        audit.transactionAction("HOLD", principal, id);
        return result;
    }

    @PostMapping("/{id}/capture")
    public TransactionSnapshot capture(@PathVariable UUID id, @Valid @RequestBody AmountRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200)
            @Pattern(regexp = "[A-Za-z0-9._:-]{1,200}") String key,
            @AuthenticationPrincipal MerchantPrincipal principal) {
        TransactionSnapshot result = service.captureForMerchant(principal.merchantId(), id,
                Money.of(request.amount(), Currency.getInstance(request.currency())), key);
        audit.transactionAction("CAPTURE", principal, id);
        return result;
    }

    @PostMapping("/{id}/release")
    public TransactionSnapshot release(@PathVariable UUID id, @Valid @RequestBody AmountRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200)
            @Pattern(regexp = "[A-Za-z0-9._:-]{1,200}") String key,
            @AuthenticationPrincipal MerchantPrincipal principal) {
        TransactionSnapshot result = service.releaseForMerchant(principal.merchantId(), id,
                Money.of(request.amount(), Currency.getInstance(request.currency())), key);
        audit.transactionAction("RELEASE", principal, id);
        return result;
    }

    @PostMapping("/{id}/expire")
    public TransactionSnapshot expire(@PathVariable UUID id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200)
            @Pattern(regexp = "[A-Za-z0-9._:-]{1,200}") String key,
            @AuthenticationPrincipal MerchantPrincipal principal) {
        TransactionSnapshot result = service.expireForMerchant(principal.merchantId(), id, key);
        audit.transactionAction("EXPIRE", principal, id);
        return result;
    }

    @GetMapping("/{id}")
    public TransactionSnapshot get(@PathVariable UUID id,
            @AuthenticationPrincipal MerchantPrincipal principal) {
        TransactionSnapshot result = service.getForMerchant(principal.merchantId(), id);
        audit.transactionAction("READ", principal, id);
        return result;
    }
}
