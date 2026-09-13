CREATE TABLE portfolios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL DEFAULT 'Minha Carteira',
    total_invested DECIMAL(18,2) DEFAULT 0,
    current_value DECIMAL(18,2) DEFAULT 0,
    total_dividends DECIMAL(18,2) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_portfolios_user ON portfolios(user_id);

CREATE TABLE portfolio_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    asset_id BIGINT NOT NULL REFERENCES assets(id),
    total_shares DECIMAL(18,8) DEFAULT 0,
    average_price DECIMAL(18,8) DEFAULT 0,
    total_invested DECIMAL(18,2) DEFAULT 0,
    current_value DECIMAL(18,2) DEFAULT 0,
    total_dividends DECIMAL(18,2) DEFAULT 0,
    is_reservation BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(portfolio_id, asset_id)
);
CREATE INDEX idx_portfolio_assets_portfolio ON portfolio_assets(portfolio_id);
CREATE INDEX idx_portfolio_assets_asset ON portfolio_assets(asset_id);
