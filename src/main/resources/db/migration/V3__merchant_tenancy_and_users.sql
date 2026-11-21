CREATE TABLE merchant (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_merchant_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

INSERT INTO merchant(id, display_name) VALUES ('default', 'Default Merchant');

ALTER TABLE payment_transaction
    ADD COLUMN merchant_id VARCHAR(64) NOT NULL DEFAULT 'default'
    REFERENCES merchant(id);

CREATE INDEX idx_payment_transaction_merchant_created
    ON payment_transaction(merchant_id, created_at DESC);

-- Rebuild the idempotency ledger with a composite tenant/key primary key.
-- Copying rows preserves all existing single-tenant records under "default"
-- and avoids relying on a database-generated primary-key constraint name.
CREATE TABLE idempotency_record_tenant (
    merchant_id VARCHAR(64) NOT NULL REFERENCES merchant(id),
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(512) NOT NULL,
    response_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_idempotency_merchant_key PRIMARY KEY (merchant_id, idempotency_key),
    CONSTRAINT ck_idempotency_completion_v3 CHECK (
        (response_json IS NULL AND completed_at IS NULL)
        OR (response_json IS NOT NULL AND completed_at IS NOT NULL)
    )
);

INSERT INTO idempotency_record_tenant(
    merchant_id, idempotency_key, request_fingerprint, response_json, created_at, completed_at
)
SELECT 'default', idempotency_key, request_fingerprint, response_json, created_at, completed_at
FROM idempotency_record;

DROP TABLE idempotency_record;
ALTER TABLE idempotency_record_tenant RENAME TO idempotency_record;
CREATE INDEX idx_idempotency_created_at ON idempotency_record(created_at);

CREATE TABLE merchant_user (
    username VARCHAR(100) PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL REFERENCES merchant(id),
    password_hash VARCHAR(100) NOT NULL,
    merchant_role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_merchant_user_role CHECK (merchant_role IN ('MERCHANT_ADMIN', 'MERCHANT_OPERATOR', 'MERCHANT_READ_ONLY'))
);

CREATE INDEX idx_merchant_user_merchant ON merchant_user(merchant_id);
