
package com.abhay.payment.domain.event;

import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.TransactionStatus;

import java.time.Instant;
import java.util.UUID;

public record AmountCaptured(
        UUID transactionId,
        Money amount,
        Instant occurredAt,
        TransactionStatus resultingStatus
) implements TransactionEvent {

    public AmountCaptured {
        if (transactionId == null
                || amount == null
                || occurredAt == null
                || resultingStatus == null) {
            throw new IllegalArgumentException(
                    "Capture event fields cannot be null"
            );
        }

        if (resultingStatus != TransactionStatus.PARTIALLY_CAPTURED
                && resultingStatus != TransactionStatus.CAPTURED) {
            throw new IllegalArgumentException(
                    "Invalid status for capture event"
            );
        }
    }
}
