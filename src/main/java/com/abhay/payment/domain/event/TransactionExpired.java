
package com.abhay.payment.domain.event;

import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.TransactionStatus;

import java.time.Instant;
import java.util.UUID;

public record TransactionExpired(
        UUID transactionId,
        Money amount,
        Instant occurredAt
) implements TransactionEvent {

    public TransactionExpired {
        if (transactionId == null || amount == null || occurredAt == null) {
            throw new IllegalArgumentException(
                    "Expiry event fields cannot be null"
            );
        }
    }

    @Override
    public TransactionStatus resultingStatus() {
        return TransactionStatus.EXPIRED;
    }
}
