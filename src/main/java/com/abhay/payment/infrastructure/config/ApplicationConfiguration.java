package com.abhay.payment.infrastructure.config;
import com.abhay.payment.application.port.ExpiryPolicy;
import com.abhay.payment.application.port.IdempotencyStore;
import com.abhay.payment.application.service.PaymentTransactionService;
import com.abhay.payment.infrastructure.expiry.FixedDurationExpiryPolicy;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.abhay.payment.domain.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.Duration;
@Configuration
@EnableScheduling
public class ApplicationConfiguration {
 @Bean Clock clock(){return Clock.systemUTC();}
 @Bean ExpiryPolicy expiryPolicy(@Value("${payment.hold.duration:PT30M}") Duration duration){return new FixedDurationExpiryPolicy(duration);}
 @Bean PaymentTransactionService paymentTransactionService(TransactionRepository repository,IdempotencyStore idempotency,ExpiryPolicy expiry,Clock clock){
  return new PaymentTransactionService(repository,idempotency,expiry,clock);
 }
}