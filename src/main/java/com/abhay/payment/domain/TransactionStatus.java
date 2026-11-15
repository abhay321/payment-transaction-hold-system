
package com.abhay.payment.domain.model;

/**
 * Represents the lifecycle states of a payment transaction.
 *
 * Terminal states:
 * CAPTURED, RELEASED and EXPIRED.
 */
public enum TransactionStatus {

    CREATED,
    HELD,
    PARTIALLY_CAPTURED,
    PARTIALLY_RELEASED,
    CAPTURED,
    RELEASED,
    EXPIRED;

    /**
     * Indicates whether the transaction has reached
     * a state where no further operations are permitted.
     */
    public boolean isTerminal() {
        return switch (this) {
            case CAPTURED, RELEASED, EXPIRED -> true;
            default -> false;
        };
    }
}
