package com.abhay.payment.application.exception;

public final class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency key was already used with a different request: " + key);
    }
}
