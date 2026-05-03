CREATE TABLE IF NOT EXISTS user_connection (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    connection_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_connection_user_connection_id UNIQUE (user_id, connection_id)
);

CREATE INDEX IF NOT EXISTS idx_user_connection_user_id ON user_connection(user_id);
CREATE INDEX IF NOT EXISTS idx_user_connection_status ON user_connection(status);
