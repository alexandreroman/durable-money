CREATE TABLE IF NOT EXISTS transfers (
    id                UUID PRIMARY KEY,
    source_account_id UUID           NOT NULL,
    target_account_id UUID           NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    status            VARCHAR(16)    NOT NULL,
    error_message     TEXT,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL
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
