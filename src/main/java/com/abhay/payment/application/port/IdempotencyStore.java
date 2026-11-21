package com.abhay.payment.application.port;

import java.util.function.Supplier;

/** Executes a request once for a key and returns its original result on retry. */
public interface IdempotencyStore {

    <T> T execute(String key, String requestFingerprint, Supplier<T> operation);

    /**
     * Tenant-aware idempotency contract. Durable adapters should persist the
     * merchant and client key as separate key columns; the default preserves
     * compatibility with in-memory learning adapters.
     */
    default <T> T execute(String merchantId, String key, String requestFingerprint,
                          Supplier<T> operation) {
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("Merchant identity is required");
        }
        return execute(merchantId + ":" + key, requestFingerprint, operation);
    }
}
