package com.abhay.payment.application.port;

import java.time.Instant;

/** Strategy for calculating the expiry instant of a newly placed hold. */
@FunctionalInterface
public interface ExpiryPolicy {

    Instant expiryAt(Instant holdPlacedAt);
}
