ALTER TABLE transaction_outbox
    ADD COLUMN dead_lettered_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE payment_event_inbox (
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    merchant_id VARCHAR(64) NOT NULL REFERENCES merchant(id),
    transaction_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment_event_inbox PRIMARY KEY (consumer_name, event_id)
);
CREATE INDEX idx_payment_event_inbox_merchant_received
    ON payment_event_inbox(merchant_id, received_at DESC);

CREATE TABLE provider_settlement_record (
    id UUID PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL REFERENCES merchant(id),
    transaction_id UUID NOT NULL,
    provider_reference VARCHAR(160) NOT NULL,
    settled_amount NUMERIC(19, 4) NOT NULL CHECK (settled_amount > 0),
    currency CHAR(3) NOT NULL,
    provider_status VARCHAR(32) NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reconciliation_status VARCHAR(16) NOT NULL,
    exception_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_provider_settlement_merchant_reference UNIQUE (merchant_id, provider_reference),
    CONSTRAINT ck_provider_settlement_reconciliation_status
        CHECK (reconciliation_status IN ('MATCHED', 'MISMATCH', 'UNMATCHED'))
);
CREATE INDEX idx_provider_settlement_exceptions
    ON provider_settlement_record(merchant_id, reconciliation_status, created_at DESC);
