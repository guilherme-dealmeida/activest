CREATE TABLE ceiling_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    asset_id BIGINT NOT NULL REFERENCES assets(id),
    max_price DECIMAL(18,8) NOT NULL,
    alert_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, asset_id)
);
CREATE INDEX idx_ceiling_prices_user ON ceiling_prices(user_id);
