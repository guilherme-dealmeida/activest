-- Tipos de ativo (Ações, FII, Tesouro Direto...). Tabela de referência.
--
-- price_source diz de ONDE vem a cotação daquele tipo. Existe para que a
-- escolha do provedor seja dado, e não comparação de string no código: antes
-- o scheduler fazia "CRYPTO".equals(name), o que quebrava silenciosamente a
-- cada renomeação ou tipo novo.
--   BRAPI      cotação da B3
--   COINGECKO  criptomoedas
--   MANUAL     sem provedor automático (renda fixa, fundos, ativos dos EUA
--              enquanto não houver cliente para eles)
--
-- fixed_income marca os tipos que têm remuneração contratada (rate_type,
-- indexer, indexer_rate em assets). É coluna, e não lista de nomes no código,
-- pela mesma razão de price_source: a tela e o service só perguntam à flag.
CREATE TABLE asset_types (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(255),
    color_hex    VARCHAR(255),
    price_source VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    fixed_income BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_asset_types_name UNIQUE (name),
    CONSTRAINT ck_asset_types_price_source CHECK (price_source IN ('BRAPI', 'COINGECKO', 'MANUAL'))
);
