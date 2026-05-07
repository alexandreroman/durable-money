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
