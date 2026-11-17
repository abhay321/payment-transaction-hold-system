package com.abhay.payment.application.dto;

import com.abhay.payment.domain.event.TransactionEvent;
import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.TransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable view of a transaction at the time an application operation completes. */
public record TransactionSnapshot(
        UUID id,
        Money originalAmount,
        Money capturedAmount,
        Money heldAmount,
        Money releasedAmount,
        TransactionStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        List<TransactionEvent> events
) {
    public TransactionSnapshot {
        events = List.copyOf(events);
    }
}
