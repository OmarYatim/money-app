CREATE TABLE IF NOT EXISTS "transaction" (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    account_id BIGINT REFERENCES account(id),
    external_account_id BIGINT,
    external_transaction_id BIGINT NOT NULL,
    date DATE NOT NULL,
    label VARCHAR(255) NOT NULL,
    wording VARCHAR(255),
    transaction_value NUMERIC(19, 4) NOT NULL DEFAULT 0,
    category VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    category_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_transaction_user_external_id UNIQUE (user_id, external_transaction_id)
);

CREATE INDEX IF NOT EXISTS idx_transaction_user_id ON "transaction"(user_id);
CREATE INDEX IF NOT EXISTS idx_transaction_account_id ON "transaction"(account_id);
CREATE INDEX IF NOT EXISTS idx_transaction_date ON "transaction"(date DESC);
CREATE INDEX IF NOT EXISTS idx_transaction_category ON "transaction"(category);
