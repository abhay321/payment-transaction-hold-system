package com.abhay.payment.infrastructure.expiry;

import com.abhay.payment.application.port.ExpiryPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Calculates hold expiry using a configured positive duration. */
public final class FixedDurationExpiryPolicy implements ExpiryPolicy {

    private final Duration duration;

    public FixedDurationExpiryPolicy(Duration duration) {
        this.duration = Objects.requireNonNull(duration, "Duration cannot be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Expiry duration must be positive");
        }
    }

    @Override
    public Instant expiryAt(Instant holdPlacedAt) {
        return Objects.requireNonNull(holdPlacedAt, "Hold time cannot be null")
                .plus(duration);
    }
}
