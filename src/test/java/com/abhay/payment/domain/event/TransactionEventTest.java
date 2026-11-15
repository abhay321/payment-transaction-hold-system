
package com.abhay.payment.domain.event;

import com.abhay.payment.domain.model.Money;
import com.abhay.payment.domain.model.Transaction;
import com.abhay.payment.domain.model.TransactionStatus;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TransactionEventTest {

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-11-15T10:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void shouldRecordTransactionCreationEvent() {

        Transaction transaction = Transaction.create(
                Money.of("1000", "INR"),
                clock
        );

        assertEquals(1, transaction.events().size());

        TransactionEvent event = transaction.events().getFirst();

        assertInstanceOf(TransactionCreated.class, event);
        assertEquals(
                TransactionStatus.CREATED,
                event.resultingStatus()
        );
    }

    @Test
    void shouldRecordHoldEvent() {

        Transaction transaction = Transaction.create(
                Money.of("1000", "INR"),
                clock
        );

        transaction.hold(clock);

        assertEquals(2, transaction.events().size());

        TransactionEvent event = transaction.events().getLast();

        assertInstanceOf(HoldPlaced.class, event);
        assertEquals(
                TransactionStatus.HELD,
                event.resultingStatus()
        );
    }

    @Test
    void shouldRecordPartialCaptureEvent() {

        Transaction transaction = Transaction.create(
                Money.of("1000", "INR"),
                clock
        );

        transaction.hold(clock);
        transaction.capture(Money.of("600", "INR"), clock);

        TransactionEvent event = transaction.events().getLast();

        assertInstanceOf(AmountCaptured.class, event);
        assertEquals(
                TransactionStatus.PARTIALLY_CAPTURED,
                event.resultingStatus()
        );
        assertEquals(
                Money.of("600", "INR"),
                event.amount()
        );
    }

    @Test
    void shouldRecordReleaseEvent() {

        Transaction transaction = Transaction.create(
                Money.of("1000", "INR"),
                clock
        );

        transaction.hold(clock);
        transaction.release(Money.of("400", "INR"), clock);

        TransactionEvent event = transaction.events().getLast();

        assertInstanceOf(AmountReleased.class, event);
        assertEquals(
                TransactionStatus.PARTIALLY_RELEASED,
                event.resultingStatus()
        );
    }

    @Test
    void shouldNotAllowExternalModificationOfEventHistory() {

        Transaction transaction = Transaction.create(
                Money.of("1000", "INR"),
                clock
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> transaction.events().clear()
        );
    }
}
