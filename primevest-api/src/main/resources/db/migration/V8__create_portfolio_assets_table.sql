-- Posição consolidada dentro de uma carteira.
-- Quantidades e preços unitários usam 8 casas decimais por causa de cripto.
--
-- A posição é por ativo E papel, não só por ativo. Dois CDBs de bancos
-- diferentes são duas linhas, cada uma com sua taxa e seu vencimento — que é o
-- ponto todo de separar o papel do catálogo. A carteira passa a repetir o mesmo
-- ticker, e a tela mostra emissor e vencimento para que as linhas se
-- distingam.
CREATE TABLE portfolio_assets (
    id              UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID    NOT NULL,
    asset_id        BIGINT  NOT NULL,
    -- Nulo para renda variável: uma ação não tem emissor nem taxa contratada.
    note_id         UUID,
    total_shares    NUMERIC(18,8),
    average_price   NUMERIC(18,8),
    total_invested  NUMERIC(18,2),
    current_value   NUMERIC(18,2),
    total_dividends NUMERIC(18,2),
    is_reservation  BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_portfolio_assets_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_assets_asset     FOREIGN KEY (asset_id)     REFERENCES assets (id),
    CONSTRAINT fk_portfolio_assets_note      FOREIGN KEY (note_id)      REFERENCES fixed_income_notes (id),
    -- NULLS NOT DISTINCT (PG 15+) é o que faz a regra valer para renda
    -- variável: no padrão SQL dois NULLs nunca são iguais, então
    -- (carteira, PETR4, NULL) não conflitaria com outro igual e a unicidade de
    -- sempre simplesmente deixaria de existir para ações e FIIs.
    CONSTRAINT uk_portfolio_assets_position  UNIQUE NULLS NOT DISTINCT (portfolio_id, asset_id, note_id)
);

CREATE INDEX idx_portfolio_assets_portfolio ON portfolio_assets (portfolio_id);
CREATE INDEX idx_portfolio_assets_asset     ON portfolio_assets (asset_id);
CREATE INDEX idx_portfolio_assets_note      ON portfolio_assets (note_id);
