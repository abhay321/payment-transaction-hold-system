package com.abhay.payment.infrastructure.persistence;

/** Adapter boundary for publishing committed domain events to a broker or event bus. */
@FunctionalInterface
public interface OutboxEventPublisher {
    void publish(OutboxMessage message);
}
