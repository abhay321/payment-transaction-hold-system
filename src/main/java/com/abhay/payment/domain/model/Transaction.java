package com.abhay.payment.domain.model;

import com.abhay.payment.domain.event.AmountCaptured;
import com.abhay.payment.domain.event.AmountReleased;
import com.abhay.payment.domain.event.HoldPlaced;
import com.abhay.payment.domain.event.TransactionCreated;
import com.abhay.payment.domain.event.TransactionEvent;
import com.abhay.payment.domain.event.TransactionExpired;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Transaction aggregate root.
 *
 * Owns the financial invariants, transaction lifecycle and the in-memory
 * history of successful domain events.
 *
 * All mutations must happen through the public business methods.
 */
public final class Transaction {

    private final UUID id;
    private final Money originalAmount;
    private final Instant createdAt;
    private final List<TransactionEvent> events = new ArrayList<>();

    private Money capturedAmount;
    private Money heldAmount;
    private Money releasedAmount;

    private TransactionStatus status;
    private Instant updatedAt;
    private Instant expiresAt;

    private Transaction(
            UUID id,
            Money originalAmount,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "Transaction id cannot be null");
        this.originalAmount = Objects.requireNonNull(
                originalAmount,
                "Original amount cannot be null"
        );

        if (!originalAmount.isPositive()) {
            throw new IllegalArgumentException(
                    "Transaction amount must be positive"
            );
        }

        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Creation time cannot be null"
        );
        this.updatedAt = createdAt;

        this.capturedAmount = zero();
        this.heldAmount = zero();
        this.releasedAmount = zero();
        this.status = TransactionStatus.CREATED;

        recordEvent(new TransactionCreated(id, originalAmount, createdAt));
    }

    /**
     * Creates a new transaction.
     */
    public static Transaction create(
            Money originalAmount,
            Clock clock
    ) {
        Objects.requireNonNull(clock, "Clock cannot be null");

        return new Transaction(
                UUID.randomUUID(),
                originalAmount,
                clock.instant()
        );
    }

    /**
     * Places the entire transaction amount on hold.
     */
    public void hold(Clock clock) {
        validateClock(clock);
        ensureStatus(TransactionStatus.CREATED);

        Instant occurredAt = clock.instant();

        heldAmount = originalAmount;
        expiresAt = null;
        status = TransactionStatus.HELD;
        updatedAt = occurredAt;

        validateInvariants();
        recordEvent(new HoldPlaced(id, originalAmount, occurredAt));
    }

    /**
     * Captures all or part of the currently held amount.
     */
    public void capture(Money amount, Clock clock) {
        validateClock(clock);
        validateOperationAmount(amount);

        ensureActiveHold();
        ensureSufficientHeldAmount(amount);

        Instant occurredAt = clock.instant();

        capturedAmount = capturedAmount.add(amount);
        heldAmount = heldAmount.subtract(amount);

        if (heldAmount.isZero()) {
            status = releasedAmount.isZero()
                    ? TransactionStatus.CAPTURED
                    : TransactionStatus.RELEASED;
        } else {
            status = TransactionStatus.PARTIALLY_CAPTURED;
        }

        updatedAt = occurredAt;

        validateInvariants();
        recordEvent(new AmountCaptured(id, amount, occurredAt, status));
    }

    /**
     * Releases all or part of the currently held amount.
     */
    public void release(Money amount, Clock clock) {
        validateClock(clock);
        validateOperationAmount(amount);

        ensureActiveHold();
        ensureSufficientHeldAmount(amount);

        Instant occurredAt = clock.instant();

        heldAmount = heldAmount.subtract(amount);
        releasedAmount = releasedAmount.add(amount);

        status = heldAmount.isZero()
                ? TransactionStatus.RELEASED
                : TransactionStatus.PARTIALLY_RELEASED;

        updatedAt = occurredAt;

        validateInvariants();
        recordEvent(new AmountReleased(id, amount, occurredAt, status));
    }

    /**
     * Expires the outstanding hold and releases its remaining amount.
     */
    public void expire(Clock clock) {
        validateClock(clock);
        ensureActiveHold();

        Instant occurredAt = clock.instant();
        if (expiresAt == null || occurredAt.isBefore(expiresAt)) {
            throw new IllegalStateException(
                    "Transaction hold has not expired"
            );
        }

        Money amountBeingReleased = heldAmount;
        releasedAmount = releasedAmount.add(amountBeingReleased);
        heldAmount = zero();

        status = TransactionStatus.EXPIRED;
        updatedAt = occurredAt;

        validateInvariants();
        recordEvent(new TransactionExpired(id, amountBeingReleased, occurredAt));
    }

    /**
     * Assigns the expiry timestamp for an active hold.
     */
    public void setExpiry(Instant expiry) {
        Objects.requireNonNull(expiry, "Expiry cannot be null");

        if (status != TransactionStatus.HELD
                && status != TransactionStatus.PARTIALLY_CAPTURED
                && status != TransactionStatus.PARTIALLY_RELEASED) {
            throw new IllegalStateException(
                    "Expiry can only be assigned to an active hold"
            );
        }

        if (!expiry.isAfter(updatedAt)) {
            throw new IllegalArgumentException(
                    "Expiry must be after the last transaction update"
            );
        }

        this.expiresAt = expiry;
    }

    private void recordEvent(TransactionEvent event) {
        events.add(Objects.requireNonNull(event, "Event cannot be null"));
    }

    /**
     * Returns an immutable snapshot of the transaction event history.
     *
     * Returning a copy prevents callers from changing the aggregate's
     * internal history.
     */
    public List<TransactionEvent> events() {
        return List.copyOf(events);
    }

    private void ensureActiveHold() {
        if (status != TransactionStatus.HELD
                && status != TransactionStatus.PARTIALLY_CAPTURED
                && status != TransactionStatus.PARTIALLY_RELEASED) {
            throw new IllegalStateException(
                    "Transaction does not have an active hold. Status: "
                            + status
            );
        }
    }

    private void ensureStatus(TransactionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Expected status " + expected + " but found " + status
            );
        }
    }

    private void validateOperationAmount(Money amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");

        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "Operation amount must be positive"
            );
        }

        if (!originalAmount.currency().equals(amount.currency())) {
            throw new IllegalArgumentException(
                    "Operation currency does not match transaction currency"
            );
        }
    }

    private void ensureSufficientHeldAmount(Money amount) {
        if (amount.isGreaterThan(heldAmount)) {
            throw new IllegalArgumentException(
                    "Operation amount exceeds remaining held amount"
            );
        }
    }

    private void validateClock(Clock clock) {
        Objects.requireNonNull(clock, "Clock cannot be null");
    }

    private Money zero() {
        return Money.of(BigDecimal.ZERO, originalAmount.currency());
    }

    /**
     * Ensures the fundamental accounting invariant.
     */
    private void validateInvariants() {
        Money accountedAmount = capturedAmount
                .add(heldAmount)
                .add(releasedAmount);

        if (!originalAmount.equals(accountedAmount)) {
            throw new IllegalStateException(
                    "Transaction financial invariant violated"
            );
        }

        if (capturedAmount.isNegative()
                || heldAmount.isNegative()
                || releasedAmount.isNegative()) {
            throw new IllegalStateException(
                    "Transaction amounts cannot be negative"
            );
        }
    }

    public UUID id() {
        return id;
    }

    public Money originalAmount() {
        return originalAmount;
    }

    public Money capturedAmount() {
        return capturedAmount;
    }

    public Money heldAmount() {
        return heldAmount;
    }

    public Money releasedAmount() {
        return releasedAmount;
    }

    public TransactionStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
