package com.abhay.payment.infrastructure.idempotency;

import com.abhay.payment.application.exception.IdempotencyConflictException;
import com.abhay.payment.application.port.IdempotencyStore;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Process-local idempotency implementation. Competing requests with the same
 * key are serialized; different keys can proceed independently.
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private record Entry(String fingerprint, Object result) { }

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(
            String key,
            String requestFingerprint,
            Supplier<T> operation
    ) {
        requireText(key, "Idempotency key");
        requireText(requestFingerprint, "Request fingerprint");
        Objects.requireNonNull(operation, "Operation cannot be null");

        Entry entry = entries.compute(key, (ignored, existing) -> {
            if (existing != null) {
                if (!existing.fingerprint().equals(requestFingerprint)) {
                    throw new IdempotencyConflictException(key);
                }
                return existing;
            }
            return new Entry(requestFingerprint, Objects.requireNonNull(
                    operation.get(), "Idempotent operation returned null"
            ));
        });

        return (T) entry.result();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }
}
