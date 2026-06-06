ALTER TABLE goal
    ADD COLUMN IF NOT EXISTS icon                         VARCHAR(50) NOT NULL DEFAULT 'flag',
    ADD COLUMN IF NOT EXISTS color                        VARCHAR(30) NOT NULL DEFAULT 'indigo',
    ADD COLUMN IF NOT EXISTS category                     VARCHAR(100) NOT NULL DEFAULT 'Other',
    ADD COLUMN IF NOT EXISTS priority                     VARCHAR(30) NOT NULL DEFAULT 'Medium',
    ADD COLUMN IF NOT EXISTS note                         VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS auto_save_enabled            BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS planned_monthly_contribution NUMERIC(19, 4) NOT NULL DEFAULT 0;
