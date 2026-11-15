package com.abhay.payment.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-11-15T10:00:00Z"),
                ZoneOffset.UTC
        );
    }

    private Transaction createTransaction() {
        return Transaction.create(
                Money.of("1000.00", "INR"),
                clock
        );
    }

    @Test
    void shouldCreateTransactionSuccessfully() {

        Transaction transaction = createTransaction();

        assertEquals(
                TransactionStatus.CREATED,
                transaction.status()
        );

        assertEquals(
                Money.of("1000.00", "INR"),
                transaction.originalAmount()
        );

        assertEquals(
                Money.of("0.00", "INR"),
                transaction.heldAmount()
        );
    }

    @Test
    void shouldRejectZeroTransactionAmount() {

        assertThrows(
                IllegalArgumentException.class,
                () -> Transaction.create(
                        Money.of("0", "INR"),
                        clock
                )
        );
    }

    @Test
    void shouldHoldFullTransactionAmount() {

        Transaction transaction = createTransaction();

        transaction.hold(clock);

        assertEquals(TransactionStatus.HELD, transaction.status());
        assertEquals(
                Money.of("1000.00", "INR"),
                transaction.heldAmount()
        );
    }

    @Test
    void shouldRejectHoldingTwice() {

        Transaction transaction = createTransaction();

        transaction.hold(clock);

        assertThrows(
                IllegalStateException.class,
                () -> transaction.hold(clock)
        );
    }

    @Test
    void shouldSupportPartialCapture() {

        Transaction transaction = createTransaction();

        transaction.hold(clock);
        transaction.capture(Money.of("600", "INR"), clock);

        assertEquals(
                TransactionStatus.PARTIALLY_CAPTURED,
                transaction.status()
        );

        assertEquals(
                Money.of("600", "INR"),
                transaction.capturedAmount()
        );

        assertEquals(
                Money.of("400", "INR"),
                transaction.heldAmount()
        );
    }

    @Test
    void shouldSupportFullCapture() {

        Transaction transaction = createTransaction();

        transaction.hold(clock);
        transaction.capture(Money.of("1000", "INR"), clock);

        assertEquals(
                TransactionStatus.CAPTURED,
                transaction.status()
        );

        assertEquals(
                Money.of("1000", "INR"),
                transaction.capturedAmount()
        );

        assertTrue(transaction.heldAmount().isZero());
    }

    @Test
    void shouldRejectCaptureBeyondHeldAmount() {

        Transaction transaction = createTransaction();

        transaction.hold(clock);

        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.capture(
                        Money.of("1200", "INR"),
                        clock
                )
        );

        assertEquals(
                Money.of("1000", "INR"),
                transaction.heldAmount()
        );
    }

    @Test
    void shouldSupportPartialRelease() {

        Transaction transaction = createTransaction();

        transaction.hold(clock);
        transaction.release(Money.of("300", "INR"), clock);

        assertEquals(
                TransactionStatus.PARTIALLY_RELEASED,
                transaction.status()
        );

        assertEquals(
                Money.of("700", "INR"),
                transaction.heldAmount()
        );

        assertEquals(
                Money.of("300", "INR"),
                transaction.releasedAmount()
        );
    }

    @Test
    void shouldReleaseRemainingAmountAfterPartialCapture() {

        Transaction transaction = createTransaction();

        transaction.hold(clock);
        transaction.capture(Money.of("600", "INR"), clock);
        transaction.release(Money.of("400", "INR"), clock);

        assertEquals(
                TransactionStatus.RELEASED,
                transaction.status()
        );

        assertEquals(
                Money.of("600", "INR"),
                transaction.capturedAmount()
        );

        assertEquals(
                Money.of("400", "INR"),
                transaction.releasedAmount()
        );

        assertTrue(transaction.heldAmount().isZero());
    }

    @Test
    void shouldRejectCaptureBeforeHold() {

        Transaction transaction = createTransaction();

        assertThrows(
                IllegalStateException.class,
                () -> transaction.capture(
                        Money.of("100", "INR"),
                        clock
                )
        );
    }

    @Test
    void shouldRejectNegativeCapture() {

        Transaction transaction = createTransaction();
        transaction.hold(clock);

        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.capture(
                        Money.of("-100", "INR"),
                        clock
                )
        );
    }

    @Test
    void shouldRejectCurrencyMismatch() {

        Transaction transaction = createTransaction();
        transaction.hold(clock);

        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.capture(
                        Money.of("100", "USD"),
                        clock
                )
        );
    }

    @Test
    void shouldRejectOperationsOnTerminalTransaction() {

        Transaction transaction = createTransaction();

        transaction.hold(clock);
        transaction.capture(Money.of("1000", "INR"), clock);

        assertThrows(
                IllegalStateException.class,
                () -> transaction.release(
                        Money.of("100", "INR"),
                        clock
                )
        );
    }

    @Test
    void shouldExpireRemainingHold() {

        Transaction transaction = createTransaction();

        transaction.hold(clock);

        Instant expiry = clock.instant().plusSeconds(3600);
        transaction.setExpiry(expiry);

        Clock afterExpiry = Clock.fixed(
                expiry.plusSeconds(1),
                ZoneOffset.UTC
        );

        transaction.expire(afterExpiry);

        assertEquals(
                TransactionStatus.EXPIRED,
                transaction.status()
        );

        assertEquals(
                Money.of("1000", "INR"),
                transaction.releasedAmount()
        );

        assertTrue(transaction.heldAmount().isZero());
    }

    @Test
    void shouldRejectExpiryBeforeExpiryTime() {

        Transaction transaction = createTransaction();

        transaction.hold(clock);
        transaction.setExpiry(clock.instant().plusSeconds(3600));

        assertThrows(
                IllegalStateException.class,
                () -> transaction.expire(clock)
        );
    }
}
