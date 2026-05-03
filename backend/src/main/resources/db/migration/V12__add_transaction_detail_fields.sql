ALTER TABLE "transaction"
    ADD COLUMN IF NOT EXISTS original_wording   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS application_date   DATE,
    ADD COLUMN IF NOT EXISTS counterparty_label VARCHAR(255);
