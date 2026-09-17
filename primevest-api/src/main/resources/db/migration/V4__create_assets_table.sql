-- Catálogo de ativos: o que o instrumento É.
--
-- Nada aqui descreve uma compra. Vencimento e remuneração já moraram nesta
-- tabela e saíram: os que são públicos e do papel (Tesouro Direto) foram para
-- asset_terms, e os de cada aplicação (CDB, LCI, debênture) para
-- fixed_income_notes, que pertence ao usuário. Enquanto estavam aqui,
-- registrar dois CDBs de bancos diferentes exigia dois ativos — num catálogo
-- global, visível na busca de todo mundo.
--
-- O catálogo não tem user_id de propósito: um ativo é o mesmo para todos.
CREATE TABLE assets (
    id                BIGSERIAL    PRIMARY KEY,
    ticker            VARCHAR(255) NOT NULL,
    name              VARCHAR(255) NOT NULL,
    asset_type_id     BIGINT       NOT NULL,
    asset_category_id BIGINT,
    current_price     NUMERIC(18,8),
    price_updated_at  TIMESTAMP,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_assets_ticker   UNIQUE (ticker),
    CONSTRAINT fk_assets_type     FOREIGN KEY (asset_type_id)     REFERENCES asset_types (id),
    CONSTRAINT fk_assets_category FOREIGN KEY (asset_category_id) REFERENCES asset_categories (id)
);

CREATE INDEX idx_assets_ticker   ON assets (ticker);
CREATE INDEX idx_assets_type     ON assets (asset_type_id);
CREATE INDEX idx_assets_category ON assets (asset_category_id);
