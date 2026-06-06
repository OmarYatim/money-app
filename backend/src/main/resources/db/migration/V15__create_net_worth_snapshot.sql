CREATE TABLE IF NOT EXISTS net_worth_snapshot (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES app_user(id),
    snapshot_date DATE NOT NULL,
    net_worth     NUMERIC(19, 4) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_net_worth_snapshot_user_date UNIQUE (user_id, snapshot_date)
);

CREATE INDEX IF NOT EXISTS idx_net_worth_snapshot_user_date
    ON net_worth_snapshot(user_id, snapshot_date);
