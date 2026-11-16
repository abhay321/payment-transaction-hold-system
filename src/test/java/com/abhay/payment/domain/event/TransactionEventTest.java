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
        Transaction transaction = createTransaction();

        assertEquals(1, transaction.events().size());

        TransactionEvent event = transaction.events().getFirst();

        assertInstanceOf(TransactionCreated.class, event);
        assertEquals(TransactionStatus.CREATED, event.resultingStatus());
        assertEquals(transaction.id(), event.transactionId());
    }

    @Test
    void shouldRecordHoldEvent() {
        Transaction transaction = createTransaction();

        transaction.hold(clock);

        assertEquals(2, transaction.events().size());

        TransactionEvent event = transaction.events().getLast();

        assertInstanceOf(HoldPlaced.class, event);
        assertEquals(TransactionStatus.HELD, event.resultingStatus());
        assertEquals(Money.of("1000", "INR"), event.amount());
    }

    @Test
    void shouldRecordPartialCaptureEvent() {
        Transaction transaction = createTransaction();

        transaction.hold(clock);
        transaction.capture(Money.of("600", "INR"), clock);

        TransactionEvent event = transaction.events().getLast();

        assertInstanceOf(AmountCaptured.class, event);
        assertEquals(TransactionStatus.PARTIALLY_CAPTURED, event.resultingStatus());
        assertEquals(Money.of("600", "INR"), event.amount());
    }

    @Test
    void shouldRecordReleaseEvent() {
        Transaction transaction = createTransaction();

        transaction.hold(clock);
        transaction.release(Money.of("400", "INR"), clock);

        TransactionEvent event = transaction.events().getLast();

        assertInstanceOf(AmountReleased.class, event);
        assertEquals(TransactionStatus.PARTIALLY_RELEASED, event.resultingStatus());
        assertEquals(Money.of("400", "INR"), event.amount());
    }

    @Test
    void shouldRecordExpiryEventWithRemainingHeldAmount() {
        Transaction transaction = createTransaction();
        transaction.hold(clock);
        transaction.capture(Money.of("250", "INR"), clock);
        transaction.setExpiry(clock.instant().plusSeconds(60));

        Clock afterExpiry = Clock.fixed(
                clock.instant().plusSeconds(61),
                ZoneOffset.UTC
        );

        transaction.expire(afterExpiry);

        TransactionEvent event = transaction.events().getLast();

        assertInstanceOf(TransactionExpired.class, event);
        assertEquals(TransactionStatus.EXPIRED, event.resultingStatus());
        assertEquals(Money.of("750", "INR"), event.amount());
    }

    @Test
    void shouldNotRecordEventWhenCaptureFails() {
        Transaction transaction = createTransaction();
        transaction.hold(clock);
        int eventCountBeforeRejectedOperation = transaction.events().size();

        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.capture(Money.of("1001", "INR"), clock)
        );

        assertEquals(eventCountBeforeRejectedOperation, transaction.events().size());
        assertEquals(Money.of("1000", "INR"), transaction.heldAmount());
    }

    @Test
    void shouldNotAllowExternalModificationOfEventHistory() {
        Transaction transaction = createTransaction();

        assertThrows(
                UnsupportedOperationException.class,
                () -> transaction.events().clear()
        );
    }

    private Transaction createTransaction() {
        return Transaction.create(Money.of("1000", "INR"), clock);
    }
}
