
package com.abhay.payment.domain.event;

import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.TransactionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a business event that has occurred
 * during the lifecycle of a transaction.
 *
 * Sealed interface restricts permitted event types.
 */
public sealed interface TransactionEvent
        permits TransactionCreated,
                HoldPlaced,
                AmountCaptured,
                AmountReleased,
                TransactionExpired {

    UUID transactionId();

    Money amount();

    Instant occurredAt();

    TransactionStatus resultingStatus();
}
