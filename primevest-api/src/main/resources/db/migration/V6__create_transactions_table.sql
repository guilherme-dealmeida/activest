CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_asset_id UUID NOT NULL REFERENCES portfolio_assets(id) ON DELETE CASCADE,
    type VARCHAR(10) NOT NULL CHECK (type IN ('BUY', 'SELL')),
    shares DECIMAL(18,8) NOT NULL,
    price_per_share DECIMAL(18,8) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    brokerage_fee DECIMAL(18,2) DEFAULT 0,
    transaction_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_transactions_portfolio_asset ON transactions(portfolio_asset_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
