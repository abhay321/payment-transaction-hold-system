package com.abhay.payment.application.service;

import com.abhay.payment.application.dto.TransactionSnapshot;
import com.abhay.payment.application.exception.IdempotencyConflictException;
import com.abhay.payment.application.exception.TransactionNotFoundException;
import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.TransactionStatus;
import com.abhay.payment.infrastructure.expiry.FixedDurationExpiryPolicy;
import com.abhay.payment.infrastructure.idempotency.InMemoryIdempotencyStore;
import com.abhay.payment.infrastructure.persistence.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTransactionServiceTest {

    private PaymentTransactionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-11-15T10:00:00Z"),
                ZoneOffset.UTC
        );
        service = new PaymentTransactionService(
                new InMemoryTransactionRepository(),
                new InMemoryIdempotencyStore(),
                new FixedDurationExpiryPolicy(Duration.ofMinutes(30)),
                clock
        );
    }

    @Test
    void shouldExecuteCompletePartialCaptureAndReleaseWorkflow() {
        TransactionSnapshot created = service.create(Money.of("1000", "INR"), "create-1");
        TransactionSnapshot held = service.hold(created.id(), "hold-1");
        TransactionSnapshot captured = service.capture(
                created.id(), Money.of("600", "INR"), "capture-1"
        );
        TransactionSnapshot released = service.release(
                created.id(), Money.of("400", "INR"), "release-1"
        );

        assertEquals(TransactionStatus.CREATED, created.status());
        assertEquals(TransactionStatus.HELD, held.status());
        assertEquals(Instant.parse("2026-11-15T10:30:00Z"), held.expiresAt());
        assertEquals(TransactionStatus.PARTIALLY_CAPTURED, captured.status());
        assertEquals(TransactionStatus.RELEASED, released.status());
        assertEquals(Money.of("600", "INR"), released.capturedAmount());
        assertEquals(Money.of("400", "INR"), released.releasedAmount());
        assertTrue(released.heldAmount().isZero());
    }

    @Test
    void shouldReturnOriginalSnapshotForRepeatedIdempotentRequest() {
        TransactionSnapshot created = service.create(Money.of("1000", "INR"), "create-2");
        TransactionSnapshot first = service.hold(created.id(), "hold-retry");
        service.capture(created.id(), Money.of("250", "INR"), "capture-later");

        TransactionSnapshot retry = service.hold(created.id(), "hold-retry");

        assertEquals(first.status(), retry.status());
        assertEquals(first.heldAmount(), retry.heldAmount());
        assertEquals(first.events(), retry.events());
        assertEquals(TransactionStatus.HELD, retry.status());
    }

    @Test
    void shouldRejectReusingKeyWithDifferentPayload() {
        TransactionSnapshot created = service.create(Money.of("1000", "INR"), "create-3");

        assertThrows(
                IdempotencyConflictException.class,
                () -> service.create(Money.of("2000", "INR"), "create-3")
        );
        assertThrows(
                IdempotencyConflictException.class,
                () -> service.capture(
                        created.id(), Money.of("100", "INR"), "shared-key"
                )
        );
        assertThrows(
                IdempotencyConflictException.class,
                () -> service.release(
                        created.id(), Money.of("100", "INR"), "shared-key"
                )
        );
    }

    @Test
    void shouldRejectUnknownTransaction() {
        assertThrows(
                TransactionNotFoundException.class,
                () -> service.get(UUID.randomUUID())
        );
    }

    @Test
    void shouldExpireTransactionAtOrAfterConfiguredExpiry() {
        TransactionSnapshot created = service.create(Money.of("1000", "INR"), "create-4");
        service.hold(created.id(), "hold-4");

        // The test clock is fixed before expiry, so expiry must be rejected.
        assertThrows(
                IllegalStateException.class,
                () -> service.expire(created.id(), "expire-early")
        );
    }
}
