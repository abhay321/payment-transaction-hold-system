package com.abhay.payment.infrastructure.persistence;

import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.Transaction;
import com.abhay.payment.domain.model.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Duration;
import java.util.Currency;
import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JdbcTransactionRepository.class, JdbcTransactionRepositoryTest.TestBeans.class})
class JdbcTransactionRepositoryTest {
    @Autowired private JdbcTransactionRepository repository;

    @Configuration
    static class TestBeans {
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }

    @Test
    void savesAndReconstitutesAggregateAndEventHistory() {
        Clock clock = Clock.systemUTC();
        Transaction transaction = Transaction.create(
                Money.of("125.00", "INR"), clock);
        repository.save(transaction);
        transaction.hold(clock);
        transaction.setExpiry(clock.instant().plus(Duration.ofMinutes(10)));
        repository.save(transaction);

        Transaction restored = repository.findById(transaction.id()).orElseThrow();
        assertThat(restored.status()).isEqualTo(TransactionStatus.HELD);
        assertThat(restored.heldAmount()).isEqualTo(Money.of("125.00", "INR"));
        assertThat(restored.events()).hasSize(2);
        assertThat(restored.expiresAt()).isNotNull();
    }
}
