ALTER TABLE payment_transaction
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1;

CREATE TABLE idempotency_record (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_fingerprint VARCHAR(512) NOT NULL,
    response_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_idempotency_completion CHECK (
        (response_json IS NULL AND completed_at IS NULL)
        OR (response_json IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE TABLE transaction_outbox (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES payment_transaction(id),
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(500)
);

CREATE INDEX idx_transaction_outbox_pending
    ON transaction_outbox(next_attempt_at, published_at, created_at);
CREATE INDEX idx_idempotency_created_at ON idempotency_record(created_at);
