
package com.abhay.payment.domain.event;

import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.TransactionStatus;

import java.time.Instant;
import java.util.UUID;

public record TransactionCreated(
        UUID transactionId,
        Money amount,
        Instant occurredAt
) implements TransactionEvent {

    public TransactionCreated {
        if (transactionId == null || amount == null || occurredAt == null) {
            throw new IllegalArgumentException(
                    "Transaction creation event fields cannot be null"
            );
        }
    }

    @Override
    public TransactionStatus resultingStatus() {
        return TransactionStatus.CREATED;
    }
}
