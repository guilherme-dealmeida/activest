-- Categorias dentro de cada tipo de ativo (CDB, Tijolo, Papel...).
CREATE TABLE asset_categories (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    description   VARCHAR(255),
    asset_type_id BIGINT       NOT NULL,
    CONSTRAINT fk_asset_categories_type  FOREIGN KEY (asset_type_id) REFERENCES asset_types (id),
    CONSTRAINT uk_asset_categories_scope UNIQUE (asset_type_id, name)
);

CREATE INDEX idx_asset_categories_type ON asset_categories (asset_type_id);
