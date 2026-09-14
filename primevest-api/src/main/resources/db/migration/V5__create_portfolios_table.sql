-- Carteira do usuário. Os totais são valores consolidados, recalculados pela aplicação.
CREATE TABLE portfolios (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    total_invested  NUMERIC(18,2),
    current_value   NUMERIC(18,2),
    total_dividends NUMERIC(18,2),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_portfolios_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_portfolios_user ON portfolios (user_id);
