package com.abhay.payment.infrastructure.kafka;

import com.abhay.payment.infrastructure.persistence.OutboxMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventInboxConsumerTest {
    @Test
    void duplicateDeliveryIsPersistedOnlyOnce() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(),
                    postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("""
                CREATE TABLE payment_event_inbox (
                    consumer_name VARCHAR(100) NOT NULL,
                    event_id UUID NOT NULL,
                    merchant_id VARCHAR(64) NOT NULL,
                    transaction_id UUID NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    payload TEXT NOT NULL,
                    received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (consumer_name, event_id)
                )
                """);
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            PaymentEventInboxConsumer consumer =
                    new PaymentEventInboxConsumer(jdbc, mapper, "consumer-test", new SimpleMeterRegistry());
            OutboxMessage event = new OutboxMessage(UUID.randomUUID(), "merchant-a",
                    UUID.randomUUID(), "AmountCaptured", "{}", Instant.now(), 0);
            String json = mapper.writeValueAsString(event);

            consumer.consume(json);
            consumer.consume(json);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_event_inbox",
                    Integer.class)).isEqualTo(1);
        }
    }
}
