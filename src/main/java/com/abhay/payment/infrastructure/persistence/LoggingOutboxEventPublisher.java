package com.abhay.payment.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.events.kafka.enabled", havingValue = "false", matchIfMissing = true)
public final class LoggingOutboxEventPublisher implements OutboxEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingOutboxEventPublisher.class);
    @Override public void publish(OutboxMessage message) {
        log.atInfo().addKeyValue("event", "payment.outbox.published")
                .addKeyValue("outboxId", message.id()).addKeyValue("merchantId", message.merchantId())
                .addKeyValue("transactionId", message.transactionId())
                .addKeyValue("eventType", message.eventType()).addKeyValue("attempt", message.attempts())
                .log("Payment domain event dispatched to local log adapter");
    }
}
