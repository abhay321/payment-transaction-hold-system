package com.abhay.payment.application.exception;

import java.util.UUID;

public final class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(UUID transactionId) {
        super("Transaction not found: " + transactionId);
    }
}
