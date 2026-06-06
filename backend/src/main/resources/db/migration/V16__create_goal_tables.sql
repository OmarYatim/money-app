CREATE TABLE IF NOT EXISTS goal (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES app_user(id),
    linked_account_id BIGINT REFERENCES account(id),
    name              VARCHAR(255) NOT NULL,
    target_amount     NUMERIC(19, 4) NOT NULL,
    target_date       DATE,
    current_amount    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    archived          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_goal_user_target_date
    ON goal(user_id, target_date);
CREATE INDEX IF NOT EXISTS idx_goal_linked_account_id
    ON goal(linked_account_id);

CREATE TABLE IF NOT EXISTS goal_contribution (
    id             BIGSERIAL PRIMARY KEY,
    goal_id        BIGINT NOT NULL REFERENCES goal(id),
    amount         NUMERIC(19, 4) NOT NULL,
    note           VARCHAR(500),
    contributed_at DATE NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_goal_contribution_goal_id
    ON goal_contribution(goal_id);
CREATE INDEX IF NOT EXISTS idx_goal_contribution_contributed_at
    ON goal_contribution(contributed_at);
