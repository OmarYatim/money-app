CREATE TABLE IF NOT EXISTS bank_connection_state (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    state VARCHAR(100) UNIQUE NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bank_connection_state_user_id ON bank_connection_state(user_id);
CREATE INDEX IF NOT EXISTS idx_bank_connection_state_state ON bank_connection_state(state);
