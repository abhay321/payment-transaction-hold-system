package com.abhay.payment.infrastructure.persistence;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Polls the transactional outbox. Publication remains at-least-once. */
@Component
public final class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final OutboxEventPublisher publisher;
    private final Clock clock;
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter deadLetterCounter;
    private final int maxAttempts;

    public OutboxDispatcher(JdbcTemplate jdbc, TransactionTemplate transactions,
                            OutboxEventPublisher publisher, Clock clock, MeterRegistry meters,
                            @Value("${payment.outbox.max-attempts:10}") int maxAttempts) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.publisher = publisher;
        this.clock = clock;
        this.publishedCounter = meters.counter("payment.outbox.published");
        this.failedCounter = meters.counter("payment.outbox.failed");
        this.deadLetterCounter = meters.counter("payment.outbox.dead_lettered");
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.poll-delay:1000}")
    public void dispatch() {
        transactions.executeWithoutResult(status -> {
            List<OutboxMessage> batch = jdbc.query("""
                SELECT o.id, t.merchant_id, o.transaction_id, o.event_type, o.payload,
                       o.occurred_at, o.attempts
                FROM transaction_outbox o
                JOIN payment_transaction t ON t.id = o.transaction_id
                WHERE o.published_at IS NULL AND o.dead_lettered_at IS NULL
                  AND o.next_attempt_at <= ?
                ORDER BY o.created_at LIMIT 50 FOR UPDATE OF o SKIP LOCKED
                """, (rs, row) -> new OutboxMessage(
                    rs.getObject("id", UUID.class), rs.getString("merchant_id"),
                    rs.getObject("transaction_id", UUID.class), rs.getString("event_type"),
                    rs.getString("payload"), rs.getTimestamp("occurred_at").toInstant(),
                    rs.getInt("attempts")
                ), Timestamp.from(clock.instant()));

            for (OutboxMessage message : batch) {
                try {
                    publisher.publish(message);
                    jdbc.update("UPDATE transaction_outbox SET published_at=?, last_error=NULL WHERE id=?",
                            Timestamp.from(clock.instant()), message.id());
                    publishedCounter.increment();
                } catch (RuntimeException ex) {
                    failedCounter.increment();
                    int nextAttempt = message.attempts() + 1;
                    if (nextAttempt >= maxAttempts) {
                        jdbc.update("""
                            UPDATE transaction_outbox SET attempts=?, last_error=?, dead_lettered_at=?
                            WHERE id=?
                            """, nextAttempt, safeMessage(ex), Timestamp.from(clock.instant()), message.id());
                        deadLetterCounter.increment();
                    } else {
                        long delaySeconds = Math.min(300, 1L << Math.min(nextAttempt, 8));
                        jdbc.update("""
                            UPDATE transaction_outbox SET attempts=?, last_error=?, next_attempt_at=?
                            WHERE id=?
                            """, nextAttempt, safeMessage(ex),
                                Timestamp.from(clock.instant().plusSeconds(delaySeconds)), message.id());
                    }
                    log.atError().addKeyValue("event", "payment.outbox.publish_failed")
                            .addKeyValue("outboxId", message.id())
                            .addKeyValue("merchantId", message.merchantId())
                            .addKeyValue("attempt", nextAttempt)
                            .log("Payment domain event dispatch failed");
                }
            }
        });
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null) return ex.getClass().getSimpleName();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
