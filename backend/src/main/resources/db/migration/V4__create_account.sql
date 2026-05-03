CREATE TABLE IF NOT EXISTS account (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    connection_id BIGINT,
    external_account_id BIGINT NOT NULL,
    institution_name VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(100),
    account_number_last_four VARCHAR(4),
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    coming NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    last_update TIMESTAMP,
    disabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_account_user_external_id UNIQUE (user_id, external_account_id)
);

CREATE INDEX IF NOT EXISTS idx_account_user_id ON account(user_id);
CREATE INDEX IF NOT EXISTS idx_account_connection_id ON account(connection_id);
