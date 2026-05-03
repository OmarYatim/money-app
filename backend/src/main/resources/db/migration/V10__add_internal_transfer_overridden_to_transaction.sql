ALTER TABLE "transaction"
    ADD COLUMN IF NOT EXISTS internal_transfer_overridden BOOLEAN NOT NULL DEFAULT FALSE;
