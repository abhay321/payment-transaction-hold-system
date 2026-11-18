package com.abhay.payment.api;

import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.Transaction;
import com.abhay.payment.domain.repository.TransactionRepository;
import com.abhay.payment.application.port.IdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("PAYMENT_API_PASSWORD", () -> "integration-test-password");
        registry.add("payment.outbox.poll-delay", () -> "60000");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionRepository repository;
    @Autowired Clock clock;
    @Autowired IdempotencyStore idempotency;

    @Test
    void apiCreateReplayAndOutboxAreDurable() throws Exception {
        String key = "create-" + UUID.randomUUID();
        String body = "{\"amount\":\"125.00\",\"currency\":\"INR\"}";

        String first = mvc.perform(post("/api/v1/transactions")
                .with(httpBasic("payment-api", "integration-test-password"))
                .header("Idempotency-Key", key).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        String second = mvc.perform(post("/api/v1/transactions")
                .with(httpBasic("payment-api", "integration-test-password"))
                .header("Idempotency-Key", key).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(first, "$.id");
        assertThat(com.jayway.jsonpath.JsonPath.read(second, "$.id").toString()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key=?", Integer.class, key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_event WHERE transaction_id=?", Integer.class, UUID.fromString(id))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_outbox WHERE transaction_id=?", Integer.class, UUID.fromString(id))).isEqualTo(1);
    }

    @Test
    void rejectsReuseOfKeyWithDifferentRequestFingerprint() throws Exception {
        String key = "collision-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/transactions")
                .with(httpBasic("payment-api", "integration-test-password"))
                .header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                .content("{\"amount\":\"10.00\",\"currency\":\"INR\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/transactions")
                .with(httpBasic("payment-api", "integration-test-password"))
                .header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                .content("{\"amount\":\"11.00\",\"currency\":\"INR\"}"))
                .andExpect(status().isConflict());
    }


    @Test
    void failedUseCaseRollsBackIdempotencyTransactionAndOutboxTogether() {
        String key = "rollback-" + UUID.randomUUID();
        Transaction tx = Transaction.create(Money.of("50.00", "INR"), clock);

        assertThatThrownBy(() -> idempotency.execute(key, "CREATE|rollback", () -> {
            repository.save(tx);
            throw new IllegalStateException("simulated failure after aggregate write");
        })).hasMessageContaining("simulated failure");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key=?",
                Integer.class, key)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_transaction WHERE id=?",
                Integer.class, tx.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_outbox WHERE transaction_id=?",
                Integer.class, tx.id())).isZero();
    }

    @Test
    void optimisticLockRejectsAStaleAggregateAcrossRepositoryLoads() {
        Transaction created = Transaction.create(Money.of("100.00", "INR"), clock);
        repository.save(created);
        Transaction first = repository.findById(created.id()).orElseThrow();
        Transaction stale = repository.findById(created.id()).orElseThrow();

        first.hold(clock);
        stale.hold(clock);
        repository.save(first);

        assertThatThrownBy(() -> repository.save(stale))
                .hasMessageContaining("modified by another request");
        assertThat(repository.findById(created.id()).orElseThrow().events()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_outbox WHERE transaction_id=?",
                Integer.class, created.id())).isEqualTo(2);
    }
}
