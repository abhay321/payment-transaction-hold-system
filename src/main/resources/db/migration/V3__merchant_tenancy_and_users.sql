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

ALTER TABLE idempotency_record
    ADD COLUMN merchant_id VARCHAR(64) NOT NULL DEFAULT 'default'
    REFERENCES merchant(id);

ALTER TABLE idempotency_record
    DROP CONSTRAINT idempotency_record_pkey;

ALTER TABLE idempotency_record
    ADD CONSTRAINT pk_idempotency_merchant_key PRIMARY KEY (merchant_id, idempotency_key);

CREATE TABLE merchant_user (
    username VARCHAR(100) PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL REFERENCES merchant(id),
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_merchant_user_role CHECK (role IN ('MERCHANT_ADMIN', 'MERCHANT_OPERATOR', 'MERCHANT_READ_ONLY'))
);

CREATE INDEX idx_merchant_user_merchant ON merchant_user(merchant_id);
