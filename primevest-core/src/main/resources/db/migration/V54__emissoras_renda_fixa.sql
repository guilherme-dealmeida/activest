-- Emissoras e distribuidoras de renda fixa (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, secao 3.1).
--
-- O titulo de renda fixa (fixed_income_notes) deixa de guardar o emissor como texto livre e passa a apontar para
-- uma empresa do cadastro: quem EMITE (banco, financeira, cooperativa, securitizadora, companhia, Tesouro Nacional)
-- e quem DISTRIBUI (corretora/DTVM, banco, financeira, cooperativa). O cadastro vem da integracao BCB + CVM
-- (adm-api) e do seed com o estado conferido (V55). Sem vinculo com issuers (L1 do plano).
--
-- Chave natural da empresa: a RAIZ do CNPJ (8 digitos) — e o que BCB, CVM e FGC tem em comum (R1). Tesouro
-- Nacional e "Instituicao nao listada" nao tem CNPJ: sao as duas linhas fixas desta migration (L9).

-- -----------------------------------------------------------------------------
-- Grupos: conglomerados prudenciais do BCB, com o nome amigavel ("Nubank")
-- -----------------------------------------------------------------------------
CREATE TABLE fixed_income_groups (
    id                BIGSERIAL    PRIMARY KEY,
    -- conglomerado prudencial do IF.data (C0084693); nulo num grupo que so existe por curadoria
    bcb_code          VARCHAR(10),
    name              VARCHAR(120) NOT NULL,
    -- MANUAL: a importacao nunca o sobrescreve (R9). AUTOMATICO: nome do BCB sem as siglas societarias
    name_source       VARCHAR(10)  NOT NULL,
    description       VARCHAR(255),
    leader_cnpj_root  VARCHAR(8),
    -- ticker do catalogo cujo logo (carga/data/logos) o grupo usa (L22); curadoria
    logo_ticker       VARCHAR(20),
    -- so existe em bases antigas do BCB (Banco Master, Dacasa): mantido para as empresas mortas do grupo
    extinct           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_fi_groups_bcb_code UNIQUE (bcb_code),
    CONSTRAINT ck_fi_groups_name_source CHECK (name_source IN ('MANUAL', 'AUTOMATICO')),
    CONSTRAINT ck_fi_groups_leader CHECK (leader_cnpj_root IS NULL OR leader_cnpj_root ~ '^[0-9]{8}$')
);

-- -----------------------------------------------------------------------------
-- Empresas: emissoras e distribuidoras
-- -----------------------------------------------------------------------------
CREATE TABLE fixed_income_issuers (
    id                 BIGSERIAL    PRIMARY KEY,
    kind               VARCHAR(25)  NOT NULL,
    cnpj_root          VARCHAR(8),
    -- CNPJ completo, quando a fonte traz (CVM); so informacao
    cnpj               VARCHAR(14),
    legal_name         VARCHAR(255) NOT NULL,
    -- nome comercial (CVM)
    trade_name         VARCHAR(255),
    -- segmento do BCB ou tipo de participante da CVM, como a fonte escreve
    segment            VARCHAR(120),
    -- ATIVA, INATIVA, CANCELADA, LIQUIDACAO_EXTRAJUDICIAL... Empresa morta nunca sai do cadastro (R3)
    status             VARCHAR(30),
    website            VARCHAR(255),
    -- R11: FGC (bancos, financeiras), FGCOOP (cooperativas e bancos cooperativos), NENHUM
    guarantor          VARCHAR(10)  NOT NULL DEFAULT 'NENHUM',
    -- R12: regra de saida dos lotes na venda de renda fixa quando a empresa e a DISTRIBUIDORA do titulo
    sale_rule          VARCHAR(15)  NOT NULL DEFAULT 'PROPORCIONAL',
    group_id           BIGINT,
    -- BCB: ligacao do conglomerado da fonte. MANUAL: curadoria, a importacao nunca a toca (R9)
    group_source       VARCHAR(10),
    -- onde a empresa apareceu (BCB, CVM_INTERMED, CVM_CIA, CVM_SECURIT), separado por virgula
    sources            VARCHAR(60),
    source_updated_at  TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_fi_issuers_group FOREIGN KEY (group_id) REFERENCES fixed_income_groups (id),
    CONSTRAINT uk_fi_issuers_cnpj_root UNIQUE (cnpj_root),
    CONSTRAINT ck_fi_issuers_kind CHECK (kind IN ('BANCO', 'FINANCEIRA', 'COOPERATIVA', 'CORRETORA_DISTRIBUIDORA',
                                                  'SECURITIZADORA', 'COMPANHIA', 'TESOURO', 'NAO_LISTADA')),
    CONSTRAINT ck_fi_issuers_cnpj_root CHECK (cnpj_root IS NULL OR cnpj_root ~ '^[0-9]{8}$'),
    CONSTRAINT ck_fi_issuers_cnpj CHECK (cnpj IS NULL OR cnpj ~ '^[0-9]{14}$'),
    CONSTRAINT ck_fi_issuers_identity CHECK (cnpj_root IS NOT NULL OR kind IN ('TESOURO', 'NAO_LISTADA')),
    CONSTRAINT ck_fi_issuers_guarantor CHECK (guarantor IN ('FGC', 'FGCOOP', 'NENHUM')),
    CONSTRAINT ck_fi_issuers_sale_rule CHECK (sale_rule IN ('PROPORCIONAL', 'UEPS', 'PEPS')),
    CONSTRAINT ck_fi_issuers_group_source CHECK (group_source IS NULL OR group_source IN ('BCB', 'MANUAL'))
);

-- as duas linhas fixas existem uma vez so
CREATE UNIQUE INDEX uk_fi_issuers_fixed_kind ON fixed_income_issuers (kind) WHERE kind IN ('TESOURO', 'NAO_LISTADA');
CREATE INDEX idx_fi_issuers_kind  ON fixed_income_issuers (kind);
CREATE INDEX idx_fi_issuers_group ON fixed_income_issuers (group_id);

INSERT INTO fixed_income_issuers (kind, legal_name, status, guarantor, sources) VALUES
    ('TESOURO',     'Tesouro Nacional',          'ATIVA', 'NENHUM', 'FIXA'),
    ('NAO_LISTADA', 'Instituição não listada', NULL, 'NENHUM', 'FIXA');

-- -----------------------------------------------------------------------------
-- Produto -> tipos de emissora e cobertura (secao 4.4, D2)
-- -----------------------------------------------------------------------------
-- issuer_kinds: tipos aceitos no seletor de emissora (alem de NAO_LISTADA, sempre aceita). So renda fixa.
-- guaranteed: o produto esta entre os creditos garantidos — FGC: Regulamento do FGC (Anexo II a Resolucao CMN
-- 4.222/2013), art. 2o: II poupanca, III deposito a prazo (CDB, RDB), V letra de cambio, VII LCI, VIII LCA; DPGE
-- pela garantia especial do mesmo regulamento. FGCoop (cooperativas e bancos cooperativos, Resolucao CMN
-- 4.284/2013): deposito a prazo (RDC), poupanca, LCI, LCA, LC. Fora: LF, LIG, CCB, CRI, CRA, debentures e nota
-- comercial (conferido em 16/10/2026). O titulo e garantido se o produto e coberto E a emissora tem garantidor.
ALTER TABLE asset_categories ADD COLUMN issuer_kinds VARCHAR(120);
ALTER TABLE asset_categories ADD COLUMN guaranteed   BOOLEAN NOT NULL DEFAULT FALSE;

-- RDC (L5): categoria e produto, como a V3 e a V7. IR e IOF pela regra geral da V52 (nenhuma linha nova).
INSERT INTO asset_categories (name, description, asset_type_id)
SELECT 'RDC', 'Recibo de Depósito Cooperativo', t.id FROM asset_types t WHERE t.name = 'RENDA_FIXA'
ON CONFLICT (asset_type_id, name) DO NOTHING;

INSERT INTO assets (ticker, name, asset_type_id, asset_category_id)
SELECT 'RDC', 'Recibo de Depósito Cooperativo', t.id, c.id
FROM asset_types t
JOIN asset_categories c ON c.asset_type_id = t.id AND c.name = 'RDC'
WHERE t.name = 'RENDA_FIXA'
ON CONFLICT (ticker) DO NOTHING;

UPDATE asset_categories c
SET issuer_kinds = v.kinds, guaranteed = v.guaranteed
FROM (VALUES
    ('CDB',                   'BANCO',                         TRUE),
    ('RDB',                   'BANCO,FINANCEIRA',              TRUE),
    ('LC',                    'FINANCEIRA',                    TRUE),
    ('LCI',                   'BANCO,FINANCEIRA,COOPERATIVA',  TRUE),
    ('LCA',                   'BANCO,COOPERATIVA',             TRUE),
    ('LF',                    'BANCO,FINANCEIRA',              FALSE),
    ('LIG',                   'BANCO,FINANCEIRA',              FALSE),
    ('DPGE',                  'BANCO,FINANCEIRA',              TRUE),
    ('POUPANCA',              'BANCO,FINANCEIRA,COOPERATIVA',  TRUE),
    ('CCB',                   'BANCO,FINANCEIRA',              FALSE),
    ('RDC',                   'COOPERATIVA',                   TRUE),
    ('CRI',                   'SECURITIZADORA',                FALSE),
    ('CRA',                   'SECURITIZADORA',                FALSE),
    ('DEBENTURE',             'COMPANHIA',                     FALSE),
    ('DEBENTURE_INCENTIVADA', 'COMPANHIA',                     FALSE),
    ('NOTA_COMERCIAL',        'COMPANHIA',                     FALSE)
) AS v (name, kinds, guaranteed)
JOIN asset_types t ON t.name = 'RENDA_FIXA'
WHERE c.asset_type_id = t.id AND c.name = v.name;

-- O Tesouro Direto tem emissora fixa (R6): as categorias dele aceitam so TESOURO.
UPDATE asset_categories c SET issuer_kinds = 'TESOURO'
FROM asset_types t
WHERE c.asset_type_id = t.id AND t.name = 'TESOURO_DIRETO';

-- -----------------------------------------------------------------------------
-- O titulo: emissora, distribuidora e codigo do papel (L10)
-- -----------------------------------------------------------------------------
ALTER TABLE fixed_income_notes ADD COLUMN issuer_id      BIGINT;
ALTER TABLE fixed_income_notes ADD COLUMN distributor_id BIGINT;
-- codigo do papel na B3 (ex.: CDBA2478FYE), opcional; maiusculo, sem espacos
ALTER TABLE fixed_income_notes ADD COLUMN paper_code     VARCHAR(30);

-- REGRAS 8.1: campo novo obrigatorio nasce com o valor padrao — "Instituicao nao listada" nas duas, para todo
-- titulo que ja existe (RF e TD). Um valor so, sem deducao por linha; o usuario corrige pela tela.
UPDATE fixed_income_notes
SET issuer_id      = (SELECT id FROM fixed_income_issuers WHERE kind = 'NAO_LISTADA'),
    distributor_id = (SELECT id FROM fixed_income_issuers WHERE kind = 'NAO_LISTADA');

ALTER TABLE fixed_income_notes ALTER COLUMN issuer_id      SET NOT NULL;
ALTER TABLE fixed_income_notes ALTER COLUMN distributor_id SET NOT NULL;
ALTER TABLE fixed_income_notes ADD CONSTRAINT fk_fixed_income_notes_issuer
    FOREIGN KEY (issuer_id) REFERENCES fixed_income_issuers (id);
ALTER TABLE fixed_income_notes ADD CONSTRAINT fk_fixed_income_notes_distributor
    FOREIGN KEY (distributor_id) REFERENCES fixed_income_issuers (id);
ALTER TABLE fixed_income_notes ADD CONSTRAINT ck_fixed_income_notes_paper_code
    CHECK (paper_code IS NULL OR paper_code ~ '^[A-Z0-9]{1,30}$');

CREATE INDEX idx_fixed_income_notes_issuer      ON fixed_income_notes (issuer_id);
CREATE INDEX idx_fixed_income_notes_distributor ON fixed_income_notes (distributor_id);

-- R8 / D4: um codigo aponta para um titulo so do usuario (outro investidor pode ter o mesmo papel).
CREATE UNIQUE INDEX uk_fixed_income_notes_paper_code
    ON fixed_income_notes (user_id, paper_code) WHERE paper_code IS NOT NULL;

-- Regra H (D3): no Tesouro, um titulo por ativo, DISTRIBUIDORA e data. Comprar o mesmo titulo no mesmo dia em
-- duas corretoras e compra legitima; o FIFO por distribuidora (R13) mantem os dois separados.
DROP INDEX uk_fixed_income_notes_purchase;
CREATE UNIQUE INDEX uk_fixed_income_notes_purchase
    ON fixed_income_notes (user_id, asset_id, distributor_id, purchase_date)
    WHERE purchase_date IS NOT NULL;

-- O emissor em texto livre sai (decisao do levantamento, L10): sem copia — o valor padrao acima ja vale para
-- todas as linhas, e a correcao dos titulos existentes e feita pelo usuario. Guarda: nenhuma linha sem as duas.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM fixed_income_notes WHERE issuer_id IS NULL OR distributor_id IS NULL) THEN
        RAISE EXCEPTION 'V54: titulo sem emissora/distribuidora; o texto livre nao sera removido';
    END IF;
END $$;
ALTER TABLE fixed_income_notes DROP COLUMN issuer;

COMMENT ON TABLE fixed_income_issuers IS
    'Emissoras e distribuidoras de renda fixa (BCB + CVM). Chave natural: raiz do CNPJ. Ver PLANO_EMISSORAS_RENDA_FIXA.';
COMMENT ON TABLE fixed_income_groups IS
    'Conglomerados prudenciais do BCB com nome amigavel. Ver PLANO_EMISSORAS_RENDA_FIXA.';
