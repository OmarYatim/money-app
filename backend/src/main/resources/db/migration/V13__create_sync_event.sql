CREATE TABLE IF NOT EXISTS sync_event (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    connection_id BIGINT,
    triggered_by VARCHAR(50) NOT NULL,
    triggered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sync_event_user_triggered_at
    ON sync_event(user_id, triggered_at DESC);
CREATE INDEX IF NOT EXISTS idx_sync_event_status
    ON sync_event(status);
