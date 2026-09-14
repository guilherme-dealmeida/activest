-- Metas de investimento do usuário.
CREATE TABLE goals (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID          NOT NULL,
    name           VARCHAR(255)  NOT NULL,
    target_amount  NUMERIC(18,2) NOT NULL,
    current_amount NUMERIC(18,2),
    target_date    DATE,
    status         VARCHAR(20)   NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_goals_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_goals_user ON goals (user_id);
