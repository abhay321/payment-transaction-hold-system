package com.abhay.payment.application.port;

import java.util.function.Supplier;

/** Executes a request once for a key and returns its original result on retry. */
public interface IdempotencyStore {

    <T> T execute(String key, String requestFingerprint, Supplier<T> operation);
}
