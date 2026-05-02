ALTER TABLE user_connection
    ADD COLUMN IF NOT EXISTS state VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_user_connection_state ON user_connection(state);
