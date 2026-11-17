package com.abhay.payment.infrastructure.expiry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FixedDurationExpiryPolicyTest {

    @Test
    void shouldCalculateExpiryFromHoldTime() {
        FixedDurationExpiryPolicy policy =
                new FixedDurationExpiryPolicy(Duration.ofMinutes(15));
        Instant heldAt = Instant.parse("2026-11-15T10:00:00Z");

        assertEquals(
                Instant.parse("2026-11-15T10:15:00Z"),
                policy.expiryAt(heldAt)
        );
    }

    @Test
    void shouldRejectZeroOrNegativeDuration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedDurationExpiryPolicy(Duration.ZERO)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedDurationExpiryPolicy(Duration.ofSeconds(-1))
        );
    }

    @Test
    void shouldRejectNullDurationAndHoldTime() {
        assertThrows(
                NullPointerException.class,
                () -> new FixedDurationExpiryPolicy(null)
        );

        FixedDurationExpiryPolicy policy =
                new FixedDurationExpiryPolicy(Duration.ofMinutes(1));
        assertThrows(NullPointerException.class, () -> policy.expiryAt(null));
    }
}
