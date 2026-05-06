CREATE TABLE IF NOT EXISTS accounts (
    id         UUID PRIMARY KEY,
    owner      VARCHAR(255)   NOT NULL,
    balance    NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL
);
