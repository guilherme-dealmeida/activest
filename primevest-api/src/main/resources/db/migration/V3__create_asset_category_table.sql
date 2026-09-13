CREATE TABLE asset_categories (
    id BIGSERIAL PRIMARY KEY,
    asset_type_id BIGINT NOT NULL REFERENCES asset_types(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    UNIQUE(asset_type_id, name)
);
CREATE INDEX idx_asset_categories_type ON asset_categories(asset_type_id);
