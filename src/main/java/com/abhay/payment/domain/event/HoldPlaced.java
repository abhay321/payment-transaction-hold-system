
package com.abhay.payment.domain.event;

import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.TransactionStatus;

import java.time.Instant;
import java.util.UUID;

public record HoldPlaced(
        UUID transactionId,
        Money amount,
        Instant occurredAt
) implements TransactionEvent {

    public HoldPlaced {
        if (transactionId == null || amount == null || occurredAt == null) {
            throw new IllegalArgumentException(
                    "Hold event fields cannot be null"
            );
        }
    }

    @Override
    public TransactionStatus resultingStatus() {
        return TransactionStatus.HELD;
    }
}
