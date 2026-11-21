package com.abhay.payment.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(UUID id, UUID transactionId, String eventType,
                            String payload, Instant occurredAt, int attempts) { }
