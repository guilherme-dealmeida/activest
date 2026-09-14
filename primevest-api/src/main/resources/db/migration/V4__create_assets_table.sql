-- Catálogo de ativos. custom = true indica ativo cadastrado manualmente pelo usuário.
--
-- Remuneração (renda fixa): três colunas que só fazem sentido juntas.
--   rate_type    COMO remunera:
--                  PREFIXADO   taxa fixa, sem índice          -> indexer_rate = 12.50 (% a.a.)
--                  POS_FIXADO  percentual de um índice        -> indexer = CDI,  indexer_rate = 110.00 (% do índice)
--                  HIBRIDO     índice mais um spread          -> indexer = IPCA, indexer_rate = 6.00 (spread % a.a.)
--   indexer      QUAL índice (nulo quando prefixado)
--   indexer_rate O número, cujo significado é dado por rate_type
--
-- Sem rate_type não dá para distinguir "110% do CDI" de "CDI + 2%": os dois
-- teriam indexer = CDI e um número, com sentidos completamente diferentes.
--
-- Renda variável (ações, FIIs, ETFs) deixa as três nulas.
CREATE TABLE assets (
    id                BIGSERIAL    PRIMARY KEY,
    ticker            VARCHAR(255) NOT NULL,
    name              VARCHAR(255) NOT NULL,
    asset_type_id     BIGINT       NOT NULL,
    asset_category_id BIGINT,
    sector            VARCHAR(255),
    current_price     NUMERIC(18,8),
    price_updated_at  TIMESTAMP,
    maturity_date     DATE,
    rate_type         VARCHAR(20),
    indexer           VARCHAR(20),
    indexer_rate      NUMERIC(10,4),
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    custom            BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_assets_ticker   UNIQUE (ticker),
    CONSTRAINT fk_assets_type     FOREIGN KEY (asset_type_id)     REFERENCES asset_types (id),
    CONSTRAINT fk_assets_category FOREIGN KEY (asset_category_id) REFERENCES asset_categories (id),
    CONSTRAINT ck_assets_rate_type CHECK (rate_type IN ('PREFIXADO', 'POS_FIXADO', 'HIBRIDO')),
    CONSTRAINT ck_assets_indexer   CHECK (indexer   IN ('CDI', 'SELIC', 'IPCA', 'IGPM', 'INPC', 'TR')),
    -- Coerência: prefixado não tem índice; pós-fixado e híbrido exigem um.
    CONSTRAINT ck_assets_rate_coherence CHECK (
        (rate_type IS NULL     AND indexer IS NULL)
        OR (rate_type = 'PREFIXADO' AND indexer IS NULL)
        OR (rate_type IN ('POS_FIXADO', 'HIBRIDO') AND indexer IS NOT NULL)
    )
);

CREATE INDEX idx_assets_ticker   ON assets (ticker);
CREATE INDEX idx_assets_type     ON assets (asset_type_id);
CREATE INDEX idx_assets_category ON assets (asset_category_id);
