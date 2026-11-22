package com.abhay.payment.infrastructure.kafka;

import com.abhay.payment.infrastructure.persistence.OutboxMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.events.kafka.enabled", havingValue = "true")
public final class PaymentEventInboxConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventInboxConsumer.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String consumerName;
    private final io.micrometer.core.instrument.Counter accepted;
    private final io.micrometer.core.instrument.Counter duplicates;

    public PaymentEventInboxConsumer(JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${payment.events.consumer-group:payment-event-inbox}") String consumerName,
            MeterRegistry meters) {
        this.jdbc = jdbc; this.mapper = mapper; this.consumerName = consumerName;
        this.accepted = meters.counter("payment.events.consumed");
        this.duplicates = meters.counter("payment.events.duplicate");
    }

    @KafkaListener(topics = "${payment.events.topic}", groupId = "${payment.events.consumer-group}")
    public void consume(String json) {
        try {
            OutboxMessage event = mapper.readValue(json, OutboxMessage.class);
            int inserted = jdbc.update("""
                INSERT INTO payment_event_inbox(consumer_name, event_id, merchant_id,
                    transaction_id, event_type, payload)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """, consumerName, event.id(), event.merchantId(), event.transactionId(),
                    event.eventType(), json);
            if (inserted == 1) {
                accepted.increment();
                log.atInfo().addKeyValue("event", "payment.event.consumed")
                        .addKeyValue("eventId", event.id())
                        .addKeyValue("merchantId", event.merchantId())
                        .addKeyValue("consumer", consumerName).log("Payment event accepted");
            } else duplicates.increment();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to persist payment event in inbox", ex);
        }
    }
}
