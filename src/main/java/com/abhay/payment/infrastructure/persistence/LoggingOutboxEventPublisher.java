package com.abhay.payment.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local demonstration publisher. Replace with a broker adapter (Kafka, SNS,
 * etc.) without changing the outbox dispatcher. Delivery is at-least-once.
 */
@Component
public final class LoggingOutboxEventPublisher implements OutboxEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingOutboxEventPublisher.class);

    @Override
    public void publish(OutboxMessage message) {
        log.atInfo().addKeyValue("event", "payment.outbox.published")
                .addKeyValue("outboxId", message.id())
                .addKeyValue("transactionId", message.transactionId())
                .addKeyValue("eventType", message.eventType())
                .addKeyValue("attempt", message.attempts())
                .log("Payment domain event dispatched");
    }
}
