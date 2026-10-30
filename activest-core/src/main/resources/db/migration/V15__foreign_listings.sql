-- Estrutura do exterior (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, 11.1, I6 e D6–D9 — etapa F3, 29/10/2026).
--
-- 1. foreign_listings: os dados de um papel listado no exterior, 1:1 com assets (I6 — o padrão de asset_terms e
--    investment_funds: o que é de um tipo só não vai para o assets). Identidade (C5): o ativo vivo é casado pelo
--    composite_figi; o ETF também pela série da SEC; a ação também pelo CIK. As origens dizem de onde veio cada
--    classificação; CURATION é a curadoria da planilha (C9), que a tarefa nunca sobrescreve. type_source LIST: o tipo veio
--    da própria lista da bolsa (marca de ETF, ou preferencial pelo símbolo) quando o OpenFIGI não acha o papel.
CREATE TABLE foreign_listings (
    asset_id           BIGINT      NOT NULL,
    exchange           VARCHAR(16),
    cik                VARCHAR(10),
    sec_series_id      VARCHAR(10),
    composite_figi     VARCHAR(12),
    figi_security_type VARCHAR(40),
    sic_code           VARCHAR(4),
    type_source        VARCHAR(12),
    category_source    VARCHAR(12),
    ticker_source      VARCHAR(12),
    first_seen_on      DATE,
    last_seen_on       DATE,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT foreign_listings_pkey PRIMARY KEY (asset_id),
    CONSTRAINT fk_foreign_listings_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE,
    CONSTRAINT uk_foreign_listings_figi UNIQUE (composite_figi),
    CONSTRAINT uk_foreign_listings_series UNIQUE (sec_series_id),
    CONSTRAINT ck_foreign_listings_exchange CHECK (exchange IS NULL
        OR exchange IN ('NYSE', 'NASDAQ', 'NYSE_AMERICAN', 'NYSE_ARCA', 'CBOE', 'IEX')),
    CONSTRAINT ck_foreign_listings_cik CHECK (cik IS NULL OR cik ~ '^[0-9]{1,10}$'),
    CONSTRAINT ck_foreign_listings_series CHECK (sec_series_id IS NULL OR sec_series_id ~ '^S[0-9]{9}$'),
    CONSTRAINT ck_foreign_listings_sic CHECK (sic_code IS NULL OR sic_code ~ '^[0-9]{3,4}$'),
    CONSTRAINT ck_foreign_listings_type_source CHECK (type_source IS NULL
        OR type_source IN ('OPENFIGI', 'LIST', 'SEC_NAREIT', 'CURATION')),
    CONSTRAINT ck_foreign_listings_category_source CHECK (category_source IS NULL
        OR category_source IN ('BDR', 'SIC', 'NAREIT', 'ETF_RULE', 'CURATION')),
    CONSTRAINT ck_foreign_listings_ticker_source CHECK (ticker_source IS NULL
        OR ticker_source IN ('LIST', 'XBRL', 'NAREIT', 'FILING_TEXT', 'FUND_SERIES', 'CURATION'))
);

COMMENT ON TABLE foreign_listings IS
    'Papel listado no exterior (Stock, REIT, ETF internacional), 1:1 com assets: bolsa, CIK, série da SEC, FIGI e as origens da classificação (PLANO_ATIVOS_INTERNACIONAIS, I6).';
COMMENT ON COLUMN foreign_listings.figi_security_type IS 'securityType do OpenFIGI (Common Stock, REIT, ETP...) — base do tipo (D5).';
COMMENT ON COLUMN foreign_listings.last_seen_on IS 'Último dia em que o papel estava na lista da bolsa (sumiço + 30 dias = saída, C7).';

CREATE INDEX idx_foreign_listings_cik ON foreign_listings (cik);

-- 2. Categorias dos três tipos (D6–D8). STOCK: os setores dos BDRs, menos "ETFs no exterior" (a mesma empresa cai no
--    mesmo setor aqui e no BDR, C4). REIT: os grupos dos setores da Nareit. ETF: o tema do fundo.
INSERT INTO asset_categories (name, description, asset_type_id)
SELECT c.name, c.description, t.id
  FROM asset_categories c
  JOIN asset_types b ON b.id = c.asset_type_id AND b.name = 'BDR'
  JOIN asset_types t ON t.name = 'STOCK'
 WHERE c.name <> 'ETFS_NO_EXTERIOR'
ON CONFLICT ON CONSTRAINT uk_asset_categories_scope DO UPDATE SET description = EXCLUDED.description;

INSERT INTO asset_categories (name, description, asset_type_id)
SELECT v.name, v.description, t.id
  FROM (VALUES
        ('RESIDENCIAL', 'Residencial'),
        ('INDUSTRIAL', 'Industrial e logística'),
        ('ESCRITORIOS', 'Escritórios'),
        ('VAREJO', 'Varejo'),
        ('DATA_CENTERS', 'Data centers'),
        ('TORRES_TELECOM', 'Torres e telecom'),
        ('SAUDE', 'Saúde'),
        ('SELF_STORAGE', 'Self storage'),
        ('HOTEIS_LAZER', 'Hotéis e lazer'),
        ('DIVERSIFICADOS', 'Diversificados'),
        ('ESPECIAIS', 'Especiais (gaming, timber e outros)'),
        ('HIPOTECAS', 'Hipotecas (mREIT)')) AS v(name, description)
  JOIN asset_types t ON t.name = 'REIT'
ON CONFLICT ON CONSTRAINT uk_asset_categories_scope DO UPDATE SET description = EXCLUDED.description;

INSERT INTO asset_categories (name, description, asset_type_id)
SELECT v.name, v.description, t.id
  FROM (VALUES
        ('ACOES_EUA', 'Ações dos EUA'),
        ('ACOES_INTERNACIONAIS', 'Ações internacionais'),
        ('RENDA_FIXA', 'Renda fixa'),
        ('IMOBILIARIO', 'Imobiliário'),
        ('OURO_COMMODITIES', 'Ouro e commodities'),
        ('CRIPTO', 'Cripto'),
        ('DIVIDENDOS_RENDA', 'Dividendos e renda'),
        ('ALAVANCADOS_INVERSOS', 'Alavancados e inversos'),
        ('OUTROS_ESTRATEGIA', 'Outros e estratégia')) AS v(name, description)
  JOIN asset_types t ON t.name = 'ETF_INTERNACIONAL'
ON CONFLICT ON CONSTRAINT uk_asset_categories_scope DO UPDATE SET description = EXCLUDED.description;
