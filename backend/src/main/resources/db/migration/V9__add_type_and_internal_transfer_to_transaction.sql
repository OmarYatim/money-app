ALTER TABLE "transaction"
    ADD COLUMN IF NOT EXISTS type              VARCHAR(50),
    ADD COLUMN IF NOT EXISTS internal_transfer BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_transaction_type ON "transaction"(type);
