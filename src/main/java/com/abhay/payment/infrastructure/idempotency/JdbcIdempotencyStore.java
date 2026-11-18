package com.abhay.payment.infrastructure.idempotency;

import com.abhay.payment.application.dto.TransactionSnapshot;
import com.abhay.payment.application.exception.IdempotencyConflictException;
import com.abhay.payment.application.port.IdempotencyStore;
import com.abhay.payment.domain.event.*;
import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.TransactionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Durable idempotency implementation. The use case, aggregate writes, audit
 * history and outbox inserts participate in this same REQUIRED transaction.
 */
@Repository
public final class JdbcIdempotencyStore implements IdempotencyStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Counter replayCounter;
    private final Counter conflictCounter;

    public JdbcIdempotencyStore(JdbcTemplate jdbc, TransactionTemplate transactions,
                                ObjectMapper mapper, Clock clock, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.mapper = mapper;
        this.clock = clock;
        this.replayCounter = meters.counter("payment.idempotency.replay");
        this.conflictCounter = meters.counter("payment.idempotency.conflict");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(String key, String fingerprint, Supplier<T> operation) {
        requireText(key, "Idempotency key");
        requireText(fingerprint, "Request fingerprint");
        Objects.requireNonNull(operation, "Operation cannot be null");

        return (T) transactions.execute(status -> {
            jdbc.update("""
                INSERT INTO idempotency_record(idempotency_key, request_fingerprint, created_at)
                VALUES (?, ?, ?) ON CONFLICT (idempotency_key) DO NOTHING
                """, key, fingerprint, Timestamp.from(clock.instant()));

            Record record = jdbc.queryForObject("""
                SELECT request_fingerprint, response_json
                FROM idempotency_record WHERE idempotency_key = ? FOR UPDATE
                """, (rs, row) -> new Record(rs.getString(1), rs.getString(2)), key);

            if (!record.fingerprint().equals(fingerprint)) {
                conflictCounter.increment();
                throw new IdempotencyConflictException(key);
            }
            if (record.responseJson() != null) {
                replayCounter.increment();
                return restore(record.responseJson());
            }

            T result = Objects.requireNonNull(operation.get(), "Idempotent operation returned null");
            try {
                jdbc.update("""
                    UPDATE idempotency_record SET response_json = ?, completed_at = ?
                    WHERE idempotency_key = ?
                    """, mapper.writeValueAsString(store((TransactionSnapshot) result)),
                        Timestamp.from(clock.instant()), key);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Idempotency response cannot be serialized", ex);
            }
            return result;
        });
    }

    private StoredSnapshot store(TransactionSnapshot snapshot) {
        List<StoredEvent> events = snapshot.events().stream().map(event -> new StoredEvent(
                event.getClass().getSimpleName(), event.transactionId(), money(event.amount()),
                event.occurredAt(), event.resultingStatus())).toList();
        return new StoredSnapshot(snapshot.id(), money(snapshot.originalAmount()),
                money(snapshot.capturedAmount()), money(snapshot.heldAmount()),
                money(snapshot.releasedAmount()), snapshot.status(), snapshot.createdAt(),
                snapshot.updatedAt(), snapshot.expiresAt(), events);
    }

    private TransactionSnapshot restore(String json) {
        try {
            StoredSnapshot stored = mapper.readValue(json, StoredSnapshot.class);
            List<TransactionEvent> events = stored.events().stream().map(this::event).toList();
            return new TransactionSnapshot(stored.id(), money(stored.originalAmount()),
                    money(stored.capturedAmount()), money(stored.heldAmount()),
                    money(stored.releasedAmount()), stored.status(), stored.createdAt(),
                    stored.updatedAt(), stored.expiresAt(), events);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored idempotency response cannot be read", ex);
        }
    }

    private TransactionEvent event(StoredEvent e) {
        Money amount = money(e.amount());
        return switch (e.type()) {
            case "TransactionCreated" -> new TransactionCreated(e.transactionId(), amount, e.occurredAt());
            case "HoldPlaced" -> new HoldPlaced(e.transactionId(), amount, e.occurredAt());
            case "AmountCaptured" -> new AmountCaptured(e.transactionId(), amount, e.occurredAt(), e.status());
            case "AmountReleased" -> new AmountReleased(e.transactionId(), amount, e.occurredAt(), e.status());
            case "TransactionExpired" -> new TransactionExpired(e.transactionId(), amount, e.occurredAt());
            default -> throw new IllegalStateException("Unknown stored idempotency event: " + e.type());
        };
    }

    private MoneyData money(Money money) {
        return new MoneyData(money.amount(), money.currency().getCurrencyCode());
    }

    private Money money(MoneyData data) {
        return Money.of(data.amount(), data.currency());
    }

    private record Record(String fingerprint, String responseJson) { }
    private record MoneyData(BigDecimal amount, String currency) { }
    private record StoredEvent(String type, UUID transactionId, MoneyData amount,
                               Instant occurredAt, TransactionStatus status) { }
    private record StoredSnapshot(UUID id, MoneyData originalAmount, MoneyData capturedAmount,
                                  MoneyData heldAmount, MoneyData releasedAmount,
                                  TransactionStatus status, Instant createdAt, Instant updatedAt,
                                  Instant expiresAt, List<StoredEvent> events) { }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
    }
}
