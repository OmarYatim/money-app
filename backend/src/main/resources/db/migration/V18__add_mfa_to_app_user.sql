ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(255),
    ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS mfa_login_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mfa_login_token_user_id ON mfa_login_token(user_id);
CREATE INDEX IF NOT EXISTS idx_mfa_login_token_expires_at ON mfa_login_token(expires_at);
