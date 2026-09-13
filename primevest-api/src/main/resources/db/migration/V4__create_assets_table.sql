CREATE TABLE assets (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    asset_type_id BIGINT NOT NULL REFERENCES asset_types(id),
    asset_category_id BIGINT REFERENCES asset_categories(id),
    sector VARCHAR(100),
    current_price DECIMAL(18,8) DEFAULT 0,
    price_updated_at TIMESTAMP,
    maturity_date DATE,
    indexer VARCHAR(20),
    indexer_rate DECIMAL(10,4),
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_assets_ticker ON assets(ticker);
CREATE INDEX idx_assets_type ON assets(asset_type_id);
CREATE INDEX idx_assets_category ON assets(asset_category_id);
