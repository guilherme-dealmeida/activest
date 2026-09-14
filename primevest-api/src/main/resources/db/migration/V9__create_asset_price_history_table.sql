-- Histórico diário de cotação por ativo.
CREATE TABLE asset_price_history (
    id             BIGSERIAL     PRIMARY KEY,
    asset_id       BIGINT        NOT NULL,
    reference_date DATE          NOT NULL,
    open_price     NUMERIC(18,8),
    close_price    NUMERIC(18,8),
    high_price     NUMERIC(18,8),
    low_price      NUMERIC(18,8),
    volume         BIGINT,
    CONSTRAINT fk_price_history_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT uk_price_history_day   UNIQUE (asset_id, reference_date)
);

CREATE INDEX idx_price_history_asset_date ON asset_price_history (asset_id, reference_date);
