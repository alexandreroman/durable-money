CREATE TABLE IF NOT EXISTS accounts (
    id         UUID PRIMARY KEY,
    owner      VARCHAR(255)   NOT NULL,
    balance    NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL
);

CREATE TABLE IF NOT EXISTS transfers (
    id                UUID PRIMARY KEY,
    source_account_id UUID           NOT NULL,
    target_account_id UUID           NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    completed_at      TIMESTAMPTZ    NOT NULL
);
