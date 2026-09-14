-- Proventos recebidos: dividendos, JCP e rendimentos de FII.
-- portfolio_asset_id é opcional para permitir registrar provento de ativo
-- que não está mais na carteira.
CREATE TABLE dividends (
    id                 BIGSERIAL     PRIMARY KEY,
    asset_id           BIGINT        NOT NULL,
    portfolio_asset_id UUID,
    amount_per_share   NUMERIC(18,8) NOT NULL,
    total_amount       NUMERIC(18,2),
    ex_date            DATE          NOT NULL,
    payment_date       DATE,
    type               VARCHAR(20)   NOT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_dividends_asset           FOREIGN KEY (asset_id)           REFERENCES assets (id),
    CONSTRAINT fk_dividends_portfolio_asset FOREIGN KEY (portfolio_asset_id) REFERENCES portfolio_assets (id) ON DELETE SET NULL,
    CONSTRAINT ck_dividends_type CHECK (type IN ('DIVIDENDO', 'JCP', 'RENDIMENTO'))
);

CREATE INDEX idx_dividends_asset           ON dividends (asset_id);
CREATE INDEX idx_dividends_portfolio_asset ON dividends (portfolio_asset_id);
CREATE INDEX idx_dividends_ex_date         ON dividends (ex_date);
