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
