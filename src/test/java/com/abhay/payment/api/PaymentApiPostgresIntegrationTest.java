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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiPostgresIntegrationTest {
    private static final String ADMIN_A_PASSWORD = "merchant-a-password-123";
    private static final String ADMIN_B_PASSWORD = "merchant-b-password-123";
    private static final String READER_PASSWORD = "merchant-reader-password-123";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("PAYMENT_MERCHANT_USERS", () -> String.join(";",
                "default|payment-api|integration-test-password|MERCHANT_ADMIN",
                "merchant-a|merchant-a-user|" + ADMIN_A_PASSWORD + "|MERCHANT_ADMIN",
                "merchant-b|merchant-b-user|" + ADMIN_B_PASSWORD + "|MERCHANT_ADMIN",
                "merchant-a|merchant-a-reader|" + READER_PASSWORD + "|MERCHANT_READ_ONLY"));
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE merchant_id='default' AND idempotency_key=?", Integer.class, key)).isEqualTo(1);
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

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE merchant_id='default' AND idempotency_key=?",
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

    @Test
    void merchantsCannotReadOrMutateAnotherMerchantsTransaction() throws Exception {
        String body = "{\"amount\":\"125.00\",\"currency\":\"INR\"}";
        String created = mvc.perform(post("/api/v1/transactions")
                .with(httpBasic("merchant-a-user", ADMIN_A_PASSWORD))
                .header("Idempotency-Key", "merchant-a-create-" + UUID.randomUUID())
                .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String transactionId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mvc.perform(get("/api/v1/transactions/" + transactionId)
                .with(httpBasic("merchant-b-user", ADMIN_B_PASSWORD)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/transactions/" + transactionId + "/hold")
                .with(httpBasic("merchant-b-user", ADMIN_B_PASSWORD))
                .header("Idempotency-Key", "merchant-b-hold-" + UUID.randomUUID()))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT merchant_id FROM payment_transaction WHERE id=?",
                String.class, UUID.fromString(transactionId))).isEqualTo("merchant-a");
    }

    @Test
    void idempotencyKeysAreScopedPerMerchant() throws Exception {
        String sharedKey = "shared-key-" + UUID.randomUUID();
        String body = "{\"amount\":\"75.00\",\"currency\":\"INR\"}";

        String merchantA = mvc.perform(post("/api/v1/transactions")
                .with(httpBasic("merchant-a-user", ADMIN_A_PASSWORD))
                .header("Idempotency-Key", sharedKey).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        String merchantB = mvc.perform(post("/api/v1/transactions")
                .with(httpBasic("merchant-b-user", ADMIN_B_PASSWORD))
                .header("Idempotency-Key", sharedKey).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        String idA = com.jayway.jsonpath.JsonPath.read(merchantA, "$.id");
        String idB = com.jayway.jsonpath.JsonPath.read(merchantB, "$.id");
        assertThat(idA).isNotEqualTo(idB);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key=? AND merchant_id IN ('merchant-a','merchant-b')",
                Integer.class, sharedKey)).isEqualTo(2);
    }

    @Test
    void readOnlyMerchantRoleCanReadButCannotCreate() throws Exception {
        String created = mvc.perform(post("/api/v1/transactions")
                .with(httpBasic("merchant-a-user", ADMIN_A_PASSWORD))
                .header("Idempotency-Key", "reader-test-" + UUID.randomUUID())
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":\"15.00\",\"currency\":\"INR\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mvc.perform(get("/api/v1/transactions/" + id)
                .with(httpBasic("merchant-a-reader", READER_PASSWORD)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/transactions")
                .with(httpBasic("merchant-a-reader", READER_PASSWORD))
                .header("Idempotency-Key", "reader-write-" + UUID.randomUUID())
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":\"15.00\",\"currency\":\"INR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestCannotChooseItsMerchantIdentity() throws Exception {
        String response = mvc.perform(post("/api/v1/transactions")
                .with(httpBasic("merchant-a-user", ADMIN_A_PASSWORD))
                .header("Idempotency-Key", "forged-merchant-" + UUID.randomUUID())
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":\"15.00\",\"currency\":\"INR\",\"merchantId\":\"merchant-b\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        assertThat(jdbc.queryForObject("SELECT merchant_id FROM payment_transaction WHERE id=?",
                String.class, UUID.fromString(id))).isEqualTo("merchant-a");
    }

    @Test
    void reconciliationMatchesCapturedSettlementAndSurfacesAmountMismatch() throws Exception {
        String key = "recon-create-" + UUID.randomUUID();
        String created = mvc.perform(post("/api/v1/transactions")
                .with(httpBasic("merchant-a-user", ADMIN_A_PASSWORD))
                .header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                .content("{\\"amount\\":\\"125.00\\",\\"currency\\":\\"INR\\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        mvc.perform(post("/api/v1/transactions/" + id + "/hold")
                .with(httpBasic("merchant-a-user", ADMIN_A_PASSWORD))
                .header("Idempotency-Key", "recon-hold-" + UUID.randomUUID()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/transactions/" + id + "/capture")
                .with(httpBasic("merchant-a-user", ADMIN_A_PASSWORD))
                .header("Idempotency-Key", "recon-capture-" + UUID.randomUUID())
                .contentType(APPLICATION_JSON)
                .content("{\\"amount\\":\\"125.00\\",\\"currency\\":\\"INR\\"}"))
                .andExpect(status().isOk());

        String matched = mvc.perform(post("/api/v1/reconciliation/settlements")
                .with(httpBasic("merchant-a-user", ADMIN_A_PASSWORD))
                .contentType(APPLICATION_JSON)
                .content("{\\"transactionId\\":\\""+id+"\\",\\"providerReference\\":\\"provider-ok-"
                        + UUID.randomUUID() + "\\",\\"settledAmount\\":\\"125.00\\",\\"currency\\":\\"INR\\","
                        + "\\"providerStatus\\":\\"CAPTURED\\",\\"settledAt\\":\\"2026-11-22T10:00:00Z\\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.read(matched, "$.reconciliation_status").toString())
                .isEqualTo("MATCHED");

        String mismatch = mvc.perform(post("/api/v1/reconciliation/settlements")
                .with(httpBasic("merchant-a-user", ADMIN_A_PASSWORD))
                .contentType(APPLICATION_JSON)
                .content("{\\"transactionId\\":\\""+id+"\\",\\"providerReference\\":\\"provider-bad-"
                        + UUID.randomUUID() + "\\",\\"settledAmount\\":\\"124.00\\",\\"currency\\":\\"INR\\","
                        + "\\"providerStatus\\":\\"CAPTURED\\",\\"settledAt\\":\\"2026-11-22T10:00:00Z\\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.read(mismatch, "$.reconciliation_status").toString())
                .isEqualTo("MISMATCH");
        mvc.perform(get("/api/v1/reconciliation/exceptions")
                .with(httpBasic("merchant-a-user", ADMIN_A_PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].reconciliation_status").value("MISMATCH"));
    }
}
