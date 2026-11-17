
package com.abhay.payment.domain.event;

import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.TransactionStatus;

import java.time.Instant;
import java.util.UUID;

public record AmountReleased(
        UUID transactionId,
        Money amount,
        Instant occurredAt,
        TransactionStatus resultingStatus
) implements TransactionEvent {

    public AmountReleased {
        if (transactionId == null
                || amount == null
                || occurredAt == null
                || resultingStatus == null) {
            throw new IllegalArgumentException(
                    "Release event fields cannot be null"
            );
        }

        if (resultingStatus != TransactionStatus.PARTIALLY_RELEASED
                && resultingStatus != TransactionStatus.RELEASED) {
            throw new IllegalArgumentException(
                    "Invalid status for release event"
            );
        }
    }
}
