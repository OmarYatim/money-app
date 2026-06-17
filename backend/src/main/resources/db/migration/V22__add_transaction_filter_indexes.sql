CREATE INDEX IF NOT EXISTS idx_transaction_user_date_id
    ON "transaction"(user_id, date DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transaction_user_reviewed
    ON "transaction"(user_id, reviewed);

CREATE INDEX IF NOT EXISTS idx_transaction_user_internal_transfer
    ON "transaction"(user_id, internal_transfer);

CREATE INDEX IF NOT EXISTS idx_transaction_user_value
    ON "transaction"(user_id, transaction_value);
