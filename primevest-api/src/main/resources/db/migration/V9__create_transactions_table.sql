-- Movimentações de compra e venda. São a fonte da verdade: a posição em
-- portfolio_assets é derivada delas por recálculo cronológico.
CREATE TABLE transactions (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_asset_id UUID          NOT NULL,
    -- O papel movimentado. Nulo para renda variável.
    --
    -- A regra "renda fixa exige nota" vive na aplicação. Um CHECK precisaria
    -- enxergar assets.asset_type_id a partir daqui, o que só um trigger faz — e
    -- trigger para validar é dívida, não garantia.
    note_id            UUID,
    type               VARCHAR(20)   NOT NULL,
    shares             NUMERIC(18,8) NOT NULL,
    price_per_share    NUMERIC(18,8) NOT NULL,
    total_amount       NUMERIC(18,2) NOT NULL,
    brokerage_fee      NUMERIC(18,2),
    transaction_date   DATE          NOT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_transactions_portfolio_asset FOREIGN KEY (portfolio_asset_id) REFERENCES portfolio_assets (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_note            FOREIGN KEY (note_id)            REFERENCES fixed_income_notes (id),
    CONSTRAINT ck_transactions_type CHECK (type IN ('BUY', 'SELL'))
);

CREATE INDEX idx_transactions_portfolio_asset ON transactions (portfolio_asset_id);
CREATE INDEX idx_transactions_note            ON transactions (note_id);
CREATE INDEX idx_transactions_date            ON transactions (transaction_date);
