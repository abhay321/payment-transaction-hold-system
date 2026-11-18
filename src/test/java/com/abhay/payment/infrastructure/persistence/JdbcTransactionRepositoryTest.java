package com.abhay.payment.infrastructure.persistence;

import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.Transaction;
import com.abhay.payment.domain.model.TransactionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JdbcTransactionRepository.class, JdbcTransactionRepositoryTest.TestBeans.class})
class JdbcTransactionRepositoryTest {
    @Autowired private JdbcTransactionRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    @Configuration
    static class TestBeans {
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }

    @Test
    void savesAggregateAuditHistoryAndOutboxAtomically() {
        Transaction transaction = Transaction.create(Money.of("125.00", "INR"), clock);
        repository.save(transaction);
        transaction.hold(clock);
        transaction.setExpiry(clock.instant().plus(Duration.ofMinutes(10)));
        repository.save(transaction);

        Transaction restored = repository.findById(transaction.id()).orElseThrow();
        assertThat(restored.status()).isEqualTo(TransactionStatus.HELD);
        assertThat(restored.heldAmount()).isEqualTo(Money.of("125.00", "INR"));
        assertThat(restored.events()).hasSize(2);
        assertThat(restored.persistenceVersion()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_event", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_outbox", Integer.class)).isEqualTo(2);
    }

    @Test
    void rejectsStaleAggregateAndDoesNotAppendDuplicateHistory() {
        Transaction created = Transaction.create(Money.of("125.00", "INR"), clock);
        repository.save(created);
        Transaction first = repository.findById(created.id()).orElseThrow();
        Transaction stale = repository.findById(created.id()).orElseThrow();

        first.hold(clock);
        stale.hold(clock);
        repository.save(first);

        assertThatThrownBy(() -> repository.save(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.findById(created.id()).orElseThrow().events()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_outbox", Integer.class)).isEqualTo(2);
    }
}
