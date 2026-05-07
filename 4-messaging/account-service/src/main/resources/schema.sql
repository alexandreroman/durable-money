CREATE TABLE IF NOT EXISTS accounts (
    id         UUID PRIMARY KEY,
    owner      VARCHAR(255)   NOT NULL,
    balance    NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL
);

CREATE TABLE IF NOT EXISTS transfers (
    transfer_id UUID           NOT NULL,
    operation   VARCHAR(16)    NOT NULL,
    account_id  UUID           NOT NULL,
    amount      NUMERIC(19, 4) NOT NULL,
    applied_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (transfer_id, operation)
);

CREATE TABLE IF NOT EXISTS dlq_messages (
    id                   UUID PRIMARY KEY,
    transfer_id          UUID,
    original_exchange    VARCHAR(255) NOT NULL,
    original_routing_key VARCHAR(255) NOT NULL,
    failure_reason       TEXT,
    failure_count        INT          NOT NULL,
    payload              TEXT         NOT NULL,
    content_type         VARCHAR(64),
    status               VARCHAR(16)  NOT NULL,
    parked_at            TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dlq_messages_status ON dlq_messages (status);
