package com.abhay.payment.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Service
public class OutboxReplayService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public OutboxReplayService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    public void replay(String merchantId, UUID eventId) {
        int updated = jdbc.update("""
            UPDATE transaction_outbox o
            SET dead_lettered_at=NULL, attempts=0, next_attempt_at=?, last_error=NULL
            FROM payment_transaction t
            WHERE o.transaction_id=t.id AND t.merchant_id=?
              AND o.id=? AND o.published_at IS NULL AND o.dead_lettered_at IS NOT NULL
            """, Timestamp.from(clock.instant()), merchantId, eventId);
        if (updated != 1) throw new IllegalArgumentException("Dead-lettered event was not found for this merchant");
    }
}
