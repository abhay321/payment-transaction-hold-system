package com.abhay.payment.infrastructure.idempotency;

import com.abhay.payment.application.exception.IdempotencyConflictException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryIdempotencyStoreTest {

    @Test
    void shouldRunSameKeyOperationOnlyOnceUnderConcurrency() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        AtomicInteger executions = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return store.execute("same-key", "same-payload", () -> {
                        executions.incrementAndGet();
                        return "accepted";
                    });
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<String> future : futures) {
                assertEquals("accepted", future.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, executions.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectSameKeyWithDifferentFingerprint() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

        assertEquals("first", store.execute("key", "payload-a", () -> "first"));

        assertThrows(
                IdempotencyConflictException.class,
                () -> store.execute("key", "payload-b", () -> "second")
        );
    }

    @Test
    void shouldNotCacheFailedOperation() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

        assertThrows(
                IllegalStateException.class,
                () -> store.execute("key", "payload", () -> {
                    throw new IllegalStateException("failed");
                })
        );

        assertEquals("recovered", store.execute("key", "payload", () -> "recovered"));
    }

    @Test
    void shouldRejectBlankKeyAndFingerprint() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

        assertThrows(IllegalArgumentException.class,
                () -> store.execute(" ", "payload", () -> "result"));
        assertThrows(IllegalArgumentException.class,
                () -> store.execute("key", " ", () -> "result"));
    }
}
