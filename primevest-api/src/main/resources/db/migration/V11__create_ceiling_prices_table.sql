-- Preço teto definido pelo usuário para um ativo.
CREATE TABLE ceiling_prices (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL,
    asset_id      BIGINT        NOT NULL,
    max_price     NUMERIC(18,8) NOT NULL,
    alert_enabled BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ceiling_prices_user  FOREIGN KEY (user_id)  REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ceiling_prices_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT uk_ceiling_prices_scope UNIQUE (user_id, asset_id)
);

CREATE INDEX idx_ceiling_prices_user ON ceiling_prices (user_id);
