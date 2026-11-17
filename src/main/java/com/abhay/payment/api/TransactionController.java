package com.abhay.payment.api;
import com.abhay.payment.application.dto.TransactionSnapshot;
import com.abhay.payment.application.service.PaymentTransactionService;
import com.abhay.payment.domain.model.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
 private final PaymentTransactionService service;
 public TransactionController(PaymentTransactionService service){this.service=service;}
 public record CreateRequest(@DecimalMin(value="0.0001") BigDecimal amount,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency){}
 public record AmountRequest(@DecimalMin(value="0.0001") BigDecimal amount,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency){}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public TransactionSnapshot create(@Valid @RequestBody CreateRequest request,@RequestHeader("Idempotency-Key") @NotBlank String key){return service.create(Money.of(request.amount(),request.currency()),key);}
 @PostMapping("/{id}/hold") public TransactionSnapshot hold(@PathVariable UUID id,@RequestHeader("Idempotency-Key") @NotBlank String key){return service.hold(id,key);}
 @PostMapping("/{id}/capture") public TransactionSnapshot capture(@PathVariable UUID id,@Valid @RequestBody AmountRequest request,@RequestHeader("Idempotency-Key") @NotBlank String key){return service.capture(id,Money.of(request.amount(),request.currency()),key);}
 @PostMapping("/{id}/release") public TransactionSnapshot release(@PathVariable UUID id,@Valid @RequestBody AmountRequest request,@RequestHeader("Idempotency-Key") @NotBlank String key){return service.release(id,Money.of(request.amount(),request.currency()),key);}
 @PostMapping("/{id}/expire") public TransactionSnapshot expire(@PathVariable UUID id,@RequestHeader("Idempotency-Key") @NotBlank String key){return service.expire(id,key);}
 @GetMapping("/{id}") public TransactionSnapshot get(@PathVariable UUID id){return service.get(id);}
}