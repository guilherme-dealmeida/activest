CREATE TABLE dividends (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES assets(id),
    portfolio_asset_id UUID REFERENCES portfolio_assets(id) ON DELETE SET NULL,
    amount_per_share DECIMAL(18,8) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    ex_date DATE NOT NULL,
    payment_date DATE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('DIVIDENDO', 'JCP', 'RENDIMENTO')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dividends_asset ON dividends(asset_id);
CREATE INDEX idx_dividends_portfolio_asset ON dividends(portfolio_asset_id);
CREATE INDEX idx_dividends_ex_date ON dividends(ex_date);

CREATE TABLE asset_price_history (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES assets(id),
    reference_date DATE NOT NULL,
    open_price DECIMAL(18,8),
    close_price DECIMAL(18,8),
    high_price DECIMAL(18,8),
    low_price DECIMAL(18,8),
    volume BIGINT,
    UNIQUE(asset_id, reference_date)
);
CREATE INDEX idx_price_history_asset_date ON asset_price_history(asset_id, reference_date);
