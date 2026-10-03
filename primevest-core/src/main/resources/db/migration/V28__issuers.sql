-- Emissores: companhias abertas e FIIs (PLANO_EMPRESAS_BALANCOS.md, F1, migration M1).
--
-- Catalogo global, como assets: o balanco da Petrobras e o mesmo para todo
-- usuario. Um emissor por CNPJ; o ativo aponta para ele (assets.issuer_id).
--
-- As colunas de COMPANY e de FII convivem na mesma tabela, nulas no outro tipo:
-- sao poucas, e a tabela unica deixa a ligacao ativo -> emissor com uma FK so.
--
-- De onde vem cada coluna:
--   COMPANY  cadastro de companhias abertas da CVM (cad_cia_aberta.csv), FCA
--            (geral e valor_mobiliario) e a classificacao setorial da B3
--            (GetDetail: setor / subsetor / segmento).
--   FII      informe mensal da CVM (inf_mensal_fii_geral). O segmento curado
--            (segment) vem do seed de emissores (M3), nunca da importacao: o
--            Segmento_Atuacao da CVM e fraco ("Multicategoria" em metade dos
--            fundos) e fica so como informacao (cvm_segment).
-- ---------------------------------------------------------------------------
CREATE TABLE issuers (
    id                     BIGSERIAL    PRIMARY KEY,
    kind                   VARCHAR(10)  NOT NULL,
    cnpj                   VARCHAR(14)  NOT NULL,
    cvm_code               INTEGER,
    legal_name             VARCHAR(255) NOT NULL,
    trade_name             VARCHAR(255),
    registration_status    VARCHAR(20),
    website                VARCHAR(255),
    -- COMPANY
    cvm_sector             VARCHAR(120),
    b3_sector              VARCHAR(120),
    b3_subsector           VARCHAR(120),
    b3_segment             VARCHAR(120),
    registration_category  VARCHAR(20),
    listing_segment        VARCHAR(80),
    fiscal_year_end_month  SMALLINT,
    statement_layout       VARCHAR(20),
    -- FII
    segment                VARCHAR(120),
    cvm_segment            VARCHAR(120),
    administrator_name     VARCHAR(255),
    administrator_cnpj     VARCHAR(14),
    management_type        VARCHAR(20),
    source_updated_at      TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_issuers_cnpj UNIQUE (cnpj),
    CONSTRAINT uk_issuers_cvm_code UNIQUE (cvm_code),
    CONSTRAINT ck_issuers_kind CHECK (kind IN ('COMPANY', 'FII')),
    CONSTRAINT ck_issuers_cnpj CHECK (cnpj ~ '^[0-9]{14}$'),
    CONSTRAINT ck_issuers_cvm_code CHECK (kind = 'COMPANY' OR cvm_code IS NULL),
    CONSTRAINT ck_issuers_fiscal_month CHECK (fiscal_year_end_month BETWEEN 1 AND 12),
    CONSTRAINT ck_issuers_layout CHECK (statement_layout IN ('COMERCIAL', 'BANCO', 'SEGURADORA'))
);
CREATE INDEX idx_issuers_kind ON issuers (kind);

-- ---------------------------------------------------------------------------
-- Ligacao ativo -> emissor (F3). Nula ate a ligacao rodar, e para os tipos que
-- nao tem emissor (BDR, ETF, Tesouro, renda fixa, cripto).
--
-- share_class        ON, PN, PNA..PND ou UNIT (so ACAO). O fracionario herda
--                    a classe do ticker sem o F.
-- issuer_link_source como a ligacao foi feita: FCA, PREFIXO (raiz do ticker),
--                    B3, ISIN ou MANUAL. MANUAL nunca e sobrescrito pela rotina.
-- ---------------------------------------------------------------------------
ALTER TABLE assets ADD COLUMN issuer_id BIGINT;
ALTER TABLE assets ADD COLUMN share_class VARCHAR(10);
ALTER TABLE assets ADD COLUMN issuer_link_source VARCHAR(10);
ALTER TABLE assets ADD CONSTRAINT fk_assets_issuer FOREIGN KEY (issuer_id) REFERENCES issuers (id);
ALTER TABLE assets ADD CONSTRAINT ck_assets_share_class
    CHECK (share_class IN ('ON', 'PN', 'PNA', 'PNB', 'PNC', 'PND', 'UNIT'));
ALTER TABLE assets ADD CONSTRAINT ck_assets_issuer_link_source
    CHECK (issuer_link_source IN ('FCA', 'PREFIXO', 'B3', 'ISIN', 'MANUAL'));
CREATE INDEX idx_assets_issuer ON assets (issuer_id);

-- ---------------------------------------------------------------------------
-- Composicao das units: KLBN11 = 1 ON + 4 PN. Vem do FCA (texto livre, lido por
-- regex na F3) ou da ligacao manual. LPA e VPA da unit = do emissor x soma.
-- ---------------------------------------------------------------------------
CREATE TABLE unit_compositions (
    asset_id     BIGINT      NOT NULL,
    share_class  VARCHAR(10) NOT NULL,
    quantity     INTEGER     NOT NULL,
    CONSTRAINT pk_unit_compositions PRIMARY KEY (asset_id, share_class),
    CONSTRAINT fk_unit_compositions_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE,
    CONSTRAINT ck_unit_compositions_class CHECK (share_class IN ('ON', 'PN', 'PNA', 'PNB', 'PNC', 'PND')),
    CONSTRAINT ck_unit_compositions_quantity CHECK (quantity > 0)
);
