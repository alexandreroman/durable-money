CREATE TABLE IF NOT EXISTS transfers (
    id                UUID PRIMARY KEY,
    source_account_id UUID           NOT NULL,
    target_account_id UUID           NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    completed_at      TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS transfer_decisions (
    transfer_id       UUID PRIMARY KEY,
    decision          VARCHAR(10)    NOT NULL,
    participants      JSONB          NOT NULL,
    source_account_id UUID           NOT NULL,
    target_account_id UUID           NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    decided_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    finalized_at      TIMESTAMPTZ
);
ALTER TABLE transfer_decisions ADD COLUMN IF NOT EXISTS finalized_at      TIMESTAMPTZ;
ALTER TABLE transfer_decisions ADD COLUMN IF NOT EXISTS source_account_id UUID;
ALTER TABLE transfer_decisions ADD COLUMN IF NOT EXISTS target_account_id UUID;
ALTER TABLE transfer_decisions ADD COLUMN IF NOT EXISTS amount            NUMERIC(19, 4);
