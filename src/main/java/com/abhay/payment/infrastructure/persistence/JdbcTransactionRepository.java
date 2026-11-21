package com.abhay.payment.infrastructure.persistence;

import com.abhay.payment.domain.event.*;
import com.abhay.payment.domain.model.*;
import com.abhay.payment.domain.repository.TenantAwareTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcTransactionRepository implements TenantAwareTransactionRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final Clock clock;

    public JdbcTransactionRepository(JdbcTemplate jdbc, TransactionTemplate transactions,
                                     ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Legacy single-tenant adapter method retained for domain/repository tests. */
    @Override
    public void save(Transaction tx) {
        save(tx, "default");
    }

    @Override
    public void save(Transaction tx, String merchantId) {
        Objects.requireNonNull(tx);
        requireMerchantId(merchantId);
        transactions.executeWithoutResult(status -> {
            long currentVersion = tx.persistenceVersion();
            if (currentVersion == 0) {
                jdbc.update("""
                    INSERT INTO payment_transaction(id, merchant_id, original_amount, currency,
                        captured_amount, held_amount, released_amount, status, created_at,
                        updated_at, expires_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, tx.id(), merchantId, tx.originalAmount().amount(),
                        tx.originalAmount().currency().getCurrencyCode(), tx.capturedAmount().amount(),
                        tx.heldAmount().amount(), tx.releasedAmount().amount(), tx.status().name(),
                        Timestamp.from(tx.createdAt()), Timestamp.from(tx.updatedAt()),
                        tx.expiresAt() == null ? null : Timestamp.from(tx.expiresAt()));
                tx.markPersistedVersion(1);
            } else {
                int updated = jdbc.update("""
                    UPDATE payment_transaction SET captured_amount=?, held_amount=?, released_amount=?,
                        status=?, updated_at=?, expires_at=?, version=version+1
                    WHERE id=? AND merchant_id=? AND version=?
                    """, tx.capturedAmount().amount(), tx.heldAmount().amount(),
                        tx.releasedAmount().amount(), tx.status().name(), Timestamp.from(tx.updatedAt()),
                        tx.expiresAt() == null ? null : Timestamp.from(tx.expiresAt()),
                        tx.id(), merchantId, currentVersion);
                if (updated != 1) {
                    throw new OptimisticLockingFailureException(
                            "Transaction was modified by another request or is not accessible");
                }
                tx.markPersistedVersion(currentVersion + 1);
            }

            Integer persistedCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM transaction_event WHERE transaction_id=?",
                    Integer.class, tx.id());
            int firstMissing = persistedCount == null ? 0 : persistedCount;
            List<TransactionEvent> history = tx.events();
            if (firstMissing > history.size()) {
                throw new IllegalStateException("Persisted audit history is ahead of the aggregate");
            }
            for (int i = firstMissing; i < history.size(); i++) {
                TransactionEvent event = history.get(i);
                jdbc.update("""
                    INSERT INTO transaction_event(transaction_id, event_type, amount, currency,
                        occurred_at, resulting_status) VALUES (?, ?, ?, ?, ?, ?)
                    """, tx.id(), event.getClass().getSimpleName(), event.amount().amount(),
                        event.amount().currency().getCurrencyCode(), Timestamp.from(event.occurredAt()),
                        event.resultingStatus().name());
                insertOutbox(tx.id(), event);
            }
        });
    }

    private void insertOutbox(UUID transactionId, TransactionEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transactionId);
        payload.put("eventType", event.getClass().getSimpleName());
        payload.put("amount", event.amount().amount());
        payload.put("currency", event.amount().currency().getCurrencyCode());
        payload.put("occurredAt", event.occurredAt());
        payload.put("resultingStatus", event.resultingStatus().name());
        try {
            Timestamp now = Timestamp.from(clock.instant());
            jdbc.update("""
                INSERT INTO transaction_outbox(id, transaction_id, event_type, payload,
                    occurred_at, created_at, next_attempt_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), transactionId, event.getClass().getSimpleName(),
                    mapper.writeValueAsString(payload), Timestamp.from(event.occurredAt()), now, now);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize transaction outbox event", ex);
        }
    }

    /** Legacy single-tenant lookup retained for repository-level tests. */
    @Override
    public Optional<Transaction> findById(UUID id) {
        return findById(id, "default");
    }

    @Override
    public Optional<Transaction> findById(UUID id, String merchantId) {
        Objects.requireNonNull(id);
        requireMerchantId(merchantId);
        List<Transaction> found = jdbc.query(
            "SELECT * FROM payment_transaction WHERE id=? AND merchant_id=?",
            (rs, row) -> {
                String currency = rs.getString("currency");
                Money original = readMoney(rs.getBigDecimal("original_amount"), currency);
                List<TransactionEvent> history = jdbc.query("""
                    SELECT * FROM transaction_event WHERE transaction_id=? ORDER BY id
                    """, (er, i) -> toEvent(id, er.getString("event_type"),
                        readMoney(er.getBigDecimal("amount"), er.getString("currency")),
                        er.getTimestamp("occurred_at").toInstant(),
                        TransactionStatus.valueOf(er.getString("resulting_status"))), id);
                Timestamp expiry = rs.getTimestamp("expires_at");
                return Transaction.restore(id, original,
                    readMoney(rs.getBigDecimal("captured_amount"), currency),
                    readMoney(rs.getBigDecimal("held_amount"), currency),
                    readMoney(rs.getBigDecimal("released_amount"), currency),
                    TransactionStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    expiry == null ? null : expiry.toInstant(), history, rs.getLong("version"));
            }, id, merchantId);
        return found.stream().findFirst();
    }

    private void requireMerchantId(String merchantId) {
        if (merchantId == null || !merchantId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid merchant identity");
        }
    }

    private Money readMoney(java.math.BigDecimal amount, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        return Money.of(amount.setScale(currency.getDefaultFractionDigits()), currency);
    }

    private TransactionEvent toEvent(UUID id, String type, Money amount, Instant at,
                                     TransactionStatus status) {
        return switch (type) {
            case "TransactionCreated" -> new TransactionCreated(id, amount, at);
            case "HoldPlaced" -> new HoldPlaced(id, amount, at);
            case "AmountCaptured" -> new AmountCaptured(id, amount, at, status);
            case "AmountReleased" -> new AmountReleased(id, amount, at, status);
            case "TransactionExpired" -> new TransactionExpired(id, amount, at);
            default -> throw new IllegalStateException("Unknown persisted event type: " + type);
        };
    }
}
