-- Posição consolidada de um ativo dentro de uma carteira.
-- Quantidades e preços unitários usam 8 casas decimais por causa de cripto.
CREATE TABLE portfolio_assets (
    id              UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID    NOT NULL,
    asset_id        BIGINT  NOT NULL,
    total_shares    NUMERIC(18,8),
    average_price   NUMERIC(18,8),
    total_invested  NUMERIC(18,2),
    current_value   NUMERIC(18,2),
    total_dividends NUMERIC(18,2),
    is_reservation  BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_portfolio_assets_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_assets_asset     FOREIGN KEY (asset_id)     REFERENCES assets (id),
    CONSTRAINT uk_portfolio_assets_position  UNIQUE (portfolio_id, asset_id)
);

CREATE INDEX idx_portfolio_assets_portfolio ON portfolio_assets (portfolio_id);
CREATE INDEX idx_portfolio_assets_asset     ON portfolio_assets (asset_id);
