-- Cadastro de moedas, câmbio e tributos do exterior (docs/planos/PLANO_CADASTRO_MOEDAS.md, etapa M1).
--
-- A moeda deixa de ser "BRL implícito": cadastro de moedas (BRL base + USD), série de câmbio por moeda (PTAX do BCB,
-- boletim de fechamento), moeda no tipo de ativo, no ativo, na transação e no provento, e as regras de tributo do
-- exterior por data (IOF de câmbio, IR sobre aplicações no exterior, retenção no país de origem).
--
-- Tudo que já existe continua em reais: as colunas novas nascem com default BRL / câmbio 1 (REGRAS_CLAUDE.md 8.1) e
-- as colunas atuais (price_per_share, total_amount, amount_per_share...) mantêm o significado. A exceção decidida (D11)
-- é o preço do ativo (assets.current_price, asset_price_history), que passa a estar na moeda do ativo — hoje todo o
-- catálogo é BRL, então nenhum dado muda.

-- -----------------------------------------------------------------------------
-- currencies: o cadastro. Código ISO 4217 como chave (D1); uma única moeda base (BRL).
-- -----------------------------------------------------------------------------
CREATE TABLE currencies (
    code         VARCHAR(3)   NOT NULL,
    name         VARCHAR(60)  NOT NULL,
    symbol       VARCHAR(6)   NOT NULL,
    decimals     SMALLINT     NOT NULL DEFAULT 2,
    is_base      BOOLEAN      NOT NULL DEFAULT FALSE,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    rate_source  VARCHAR(20)  NOT NULL DEFAULT 'BCB_PTAX',
    country_code VARCHAR(2),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT currencies_pkey PRIMARY KEY (code),
    CONSTRAINT ck_currencies_code CHECK ((code ~ '^[A-Z]{3}$')),
    CONSTRAINT ck_currencies_decimals CHECK (((decimals >= 0) AND (decimals <= 8))),
    CONSTRAINT ck_currencies_rate_source CHECK ((rate_source IN ('NONE', 'BCB_PTAX'))),
    CONSTRAINT ck_currencies_base_source CHECK (((NOT is_base) OR (rate_source = 'NONE'))),
    CONSTRAINT ck_currencies_base_active CHECK (((NOT is_base) OR active)),
    CONSTRAINT ck_currencies_country CHECK (((country_code IS NULL) OR (country_code ~ '^[A-Z]{2}$')))
);
CREATE UNIQUE INDEX uk_currencies_base ON currencies (is_base) WHERE is_base;

COMMENT ON COLUMN currencies.country_code IS
    'País da moeda (ISO 3166). Usado para achar a retenção do país de origem quando o ativo não informa o país; nulo em moedas de vários países (EUR).';

INSERT INTO currencies (code, name, symbol, decimals, is_base, active, rate_source, country_code) VALUES
    ('BRL', 'Real', 'R$', 2, true, true, 'NONE', 'BR'),
    ('USD', 'Dólar americano', 'US$', 2, false, true, 'BCB_PTAX', 'US');

-- -----------------------------------------------------------------------------
-- exchange_rates: câmbio da moeda para a moeda base, um boletim de fechamento por dia (D22).
-- -----------------------------------------------------------------------------
CREATE TABLE exchange_rates (
    currency_code  VARCHAR(3)    NOT NULL,
    reference_date DATE          NOT NULL,
    buy_rate       NUMERIC(18,8) NOT NULL,
    sell_rate      NUMERIC(18,8) NOT NULL,
    bulletin_at    TIMESTAMP,
    source         VARCHAR(20)   NOT NULL DEFAULT 'BCB_PTAX',
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_exchange_rates PRIMARY KEY (currency_code, reference_date),
    CONSTRAINT fk_exchange_rates_currency FOREIGN KEY (currency_code) REFERENCES currencies(code),
    CONSTRAINT ck_exchange_rates_positive CHECK (((buy_rate > (0)) AND (sell_rate > (0)))),
    CONSTRAINT ck_exchange_rates_source CHECK ((source IN ('BCB_PTAX')))
);

-- -----------------------------------------------------------------------------
-- foreign_tax_rules: tributos do exterior por data, no estilo de withholding_tax_rules e fixed_income_tax_rules
-- (vigência + base legal). Mudança de alíquota = migration com a base legal (D18).
-- -----------------------------------------------------------------------------
CREATE TABLE foreign_tax_rules (
    id            BIGSERIAL,
    tax           VARCHAR(20)  NOT NULL,
    event         VARCHAR(30)  NOT NULL,
    country_code  VARCHAR(2),
    asset_type_id BIGINT,
    rate          NUMERIC(6,4) NOT NULL,
    brackets      JSONB,
    valid_from    DATE,
    valid_to      DATE,
    legal_basis   VARCHAR(300) NOT NULL,
    CONSTRAINT foreign_tax_rules_pkey PRIMARY KEY (id),
    CONSTRAINT fk_foreign_tax_rules_asset_type FOREIGN KEY (asset_type_id) REFERENCES asset_types(id),
    CONSTRAINT ck_foreign_tax_rules_tax CHECK ((tax IN ('IOF_FX', 'IR_FOREIGN', 'WITHHOLDING_FOREIGN'))),
    CONSTRAINT ck_foreign_tax_rules_event CHECK ((event IN ('REMITTANCE_INVESTMENT', 'REMITTANCE_OWN_ACCOUNT', 'INFLOW',
        'DIVIDEND', 'SALE'))),
    CONSTRAINT ck_foreign_tax_rules_rate CHECK (((rate >= (0)) AND (rate < (1)))),
    CONSTRAINT ck_foreign_tax_rules_validity CHECK (((valid_from IS NULL) OR (valid_to IS NULL) OR (valid_from <= valid_to))),
    CONSTRAINT ck_foreign_tax_rules_country CHECK (((country_code IS NULL) OR (country_code ~ '^[A-Z]{2}$')))
);

COMMENT ON COLUMN foreign_tax_rules.brackets IS
    'Faixas progressivas e isenção (JSON): {"monthlyExemption": 35000, "brackets": [{"upTo": 5000000, "rate": 0.15}, ...]}. Nulo = alíquota única (rate).';

-- Seed (plano, seção 9.4). As bases legais são validadas pelo Guilherme (Quem faz, 10.8).
INSERT INTO foreign_tax_rules (tax, event, country_code, rate, brackets, valid_from, valid_to, legal_basis) VALUES
    ('IOF_FX', 'REMITTANCE_INVESTMENT', NULL, 0.0110, NULL, NULL, NULL,
        'Decreto 6.306/2007 (RIOF), art. 15-B — câmbio para investimento no exterior: 1,1%'),
    ('IOF_FX', 'REMITTANCE_OWN_ACCOUNT', NULL, 0.0110, NULL, NULL, '2025-05-22',
        'Decreto 6.306/2007 (RIOF), art. 15-B — disponibilidade em conta própria no exterior: 1,1%'),
    ('IOF_FX', 'REMITTANCE_OWN_ACCOUNT', NULL, 0.0350, NULL, '2025-05-23', NULL,
        'Decretos 12.466/2025 e 12.499/2025 (RIOF, art. 15-B) — disponibilidade em conta própria no exterior: 3,5%'),
    ('IOF_FX', 'INFLOW', NULL, 0.0038, NULL, NULL, NULL,
        'Decreto 6.306/2007 (RIOF), art. 15-B — ingresso de recursos sem alíquota específica: 0,38%'),
    ('WITHHOLDING_FOREIGN', 'DIVIDEND', 'US', 0.3000, NULL, NULL, NULL,
        'EUA — Internal Revenue Code, §1441: 30% retidos de não residente (sem tratado Brasil–EUA; W-8BEN)'),
    ('IR_FOREIGN', 'DIVIDEND', NULL, 0.1500, NULL, '2024-01-01', NULL,
        'Lei 14.754/2023, arts. 2º e 4º — 15% na declaração anual, com dedução do imposto pago no exterior'),
    ('IR_FOREIGN', 'SALE', NULL, 0.1500, NULL, '2024-01-01', NULL,
        'Lei 14.754/2023, arts. 2º e 3º — 15% sobre o ganho em reais (variação cambial incluída), declaração anual'),
    ('IR_FOREIGN', 'DIVIDEND', NULL, 0.2750, NULL, NULL, '2023-12-31',
        'Lei 7.713/1988 e Lei 9.250/1995 (carnê-leão) — tabela progressiva, alíquota máxima 27,5%, com dedução do imposto pago no exterior'),
    ('IR_FOREIGN', 'SALE', NULL, 0.1500, '{"monthlyExemption": 35000}', NULL, '2016-12-31',
        'Lei 8.981/1995, art. 21 — ganho de capital 15%; isenção de vendas até R$ 35 mil no mês (Lei 9.250/1995, art. 22, II)'),
    ('IR_FOREIGN', 'SALE', NULL, 0.1500,
        '{"monthlyExemption": 35000, "brackets": [{"upTo": 5000000, "rate": 0.15}, {"upTo": 10000000, "rate": 0.175}, {"upTo": 30000000, "rate": 0.20}, {"upTo": null, "rate": 0.225}]}',
        '2017-01-01', '2023-12-31',
        'Lei 8.981/1995, art. 21 (redação da Lei 13.259/2016) — ganho de capital progressivo 15% a 22,5%; isenção de vendas até R$ 35 mil no mês (Lei 9.250/1995, art. 22, II)');

-- -----------------------------------------------------------------------------
-- fx_operations: operação de câmbio do usuário (remessa/retorno), insumo do saldo em moeda estrangeira e do custo
-- do dólar (D19: tabela, serviço e API; a tela nasce com os ativos internacionais).
-- -----------------------------------------------------------------------------
CREATE TABLE fx_operations (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_id             UUID          NOT NULL,
    operation_date      DATE          NOT NULL,
    from_currency_code  VARCHAR(3)    NOT NULL,
    to_currency_code    VARCHAR(3)    NOT NULL,
    amount_sent         NUMERIC(18,2) NOT NULL,
    amount_received     NUMERIC(18,2) NOT NULL,
    effective_rate      NUMERIC(18,8) NOT NULL,
    purpose             VARCHAR(30)   NOT NULL,
    iof_rate            NUMERIC(6,4)  NOT NULL DEFAULT 0,
    iof_amount          NUMERIC(18,2) NOT NULL DEFAULT 0,
    fee_amount          NUMERIC(18,2) NOT NULL DEFAULT 0,
    institution         VARCHAR(120),
    foreign_tax_rule_id BIGINT,
    notes               VARCHAR(500),
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fx_operations_pkey PRIMARY KEY (id),
    CONSTRAINT fk_fx_operations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_fx_operations_from FOREIGN KEY (from_currency_code) REFERENCES currencies(code),
    CONSTRAINT fk_fx_operations_to FOREIGN KEY (to_currency_code) REFERENCES currencies(code),
    CONSTRAINT fk_fx_operations_rule FOREIGN KEY (foreign_tax_rule_id) REFERENCES foreign_tax_rules(id),
    CONSTRAINT ck_fx_operations_pair CHECK ((from_currency_code <> to_currency_code)),
    CONSTRAINT ck_fx_operations_amounts CHECK (((amount_sent > (0)) AND (amount_received > (0)) AND (effective_rate > (0))
        AND (iof_amount >= (0)) AND (fee_amount >= (0)))),
    CONSTRAINT ck_fx_operations_purpose CHECK ((purpose IN ('INVESTMENT', 'OWN_ACCOUNT', 'INFLOW')))
);
CREATE INDEX idx_fx_operations_user ON fx_operations (user_id, operation_date);

-- -----------------------------------------------------------------------------
-- Moeda e região do tipo de ativo (D2, D4) e moeda do ativo (D2, D17, D25).
-- -----------------------------------------------------------------------------
ALTER TABLE asset_types
    ADD COLUMN default_currency_code VARCHAR(3)  NOT NULL DEFAULT 'BRL',
    ADD COLUMN region                VARCHAR(10) NOT NULL DEFAULT 'BRASIL',
    ADD CONSTRAINT fk_asset_types_currency FOREIGN KEY (default_currency_code) REFERENCES currencies(code),
    ADD CONSTRAINT ck_asset_types_region CHECK ((region IN ('BRASIL', 'EXTERIOR', 'CRIPTO')));

UPDATE asset_types SET default_currency_code = 'USD', region = 'EXTERIOR' WHERE name IN ('STOCK', 'REIT', 'ETF_INTERNACIONAL');
UPDATE asset_types SET region = 'EXTERIOR' WHERE name = 'BDR';
UPDATE asset_types SET region = 'CRIPTO' WHERE name = 'CRIPTO';

ALTER TABLE assets
    ADD COLUMN currency_code           VARCHAR(3) NOT NULL DEFAULT 'BRL',
    ADD COLUMN reference_currency_code VARCHAR(3),
    ADD COLUMN country_code            VARCHAR(2),
    ADD CONSTRAINT fk_assets_currency FOREIGN KEY (currency_code) REFERENCES currencies(code),
    ADD CONSTRAINT fk_assets_reference_currency FOREIGN KEY (reference_currency_code) REFERENCES currencies(code),
    ADD CONSTRAINT ck_assets_country CHECK (((country_code IS NULL) OR (country_code ~ '^[A-Z]{2}$')));

COMMENT ON COLUMN assets.currency_code IS 'Moeda em que o ativo é cotado (primária, D2); current_price e asset_price_history estão nela (D11).';
COMMENT ON COLUMN assets.reference_currency_code IS 'Moeda que o ativo acompanha, sem ser cotado nela (stablecoins: USDT/USDC → USD, D17).';
COMMENT ON COLUMN assets.country_code IS 'País de origem (ISO 3166) para a retenção do exterior; nulo = o país da moeda.';

UPDATE assets SET reference_currency_code = 'USD' WHERE ticker IN ('USDT', 'USDC');

-- -----------------------------------------------------------------------------
-- Transação: moeda, câmbio e valores originais (D3). As colunas atuais continuam em reais.
-- -----------------------------------------------------------------------------
ALTER TABLE transactions
    ADD COLUMN currency_code            VARCHAR(3)    NOT NULL DEFAULT 'BRL',
    ADD COLUMN fx_rate                  NUMERIC(18,8) NOT NULL DEFAULT 1,
    ADD COLUMN fx_rate_origin           VARCHAR(10)   NOT NULL DEFAULT 'BASE',
    ADD COLUMN original_price_per_share NUMERIC(18,8),
    ADD COLUMN original_total_amount    NUMERIC(18,2),
    ADD COLUMN original_fee             NUMERIC(18,2),
    ADD CONSTRAINT fk_transactions_currency FOREIGN KEY (currency_code) REFERENCES currencies(code),
    ADD CONSTRAINT ck_transactions_fx_origin CHECK ((fx_rate_origin IN ('BASE', 'PTAX', 'INFORMED'))),
    ADD CONSTRAINT ck_transactions_fx CHECK ((
        ((currency_code = 'BRL') AND (fx_rate = 1) AND (fx_rate_origin = 'BASE') AND (original_price_per_share IS NULL)
            AND (original_total_amount IS NULL) AND (original_fee IS NULL))
        OR ((currency_code <> 'BRL') AND (fx_rate > 0) AND (fx_rate_origin <> 'BASE') AND (original_total_amount IS NOT NULL))
    ));

-- -----------------------------------------------------------------------------
-- Provento: moeda, câmbio da data de pagamento (D24), valores originais e retenção do exterior.
-- -----------------------------------------------------------------------------
ALTER TABLE dividends
    ADD COLUMN currency_code             VARCHAR(3)    NOT NULL DEFAULT 'BRL',
    ADD COLUMN fx_rate                   NUMERIC(18,8) NOT NULL DEFAULT 1,
    ADD COLUMN original_amount_per_share NUMERIC(18,8),
    ADD COLUMN original_total_amount     NUMERIC(18,2),
    ADD COLUMN foreign_withholding_rate  NUMERIC(6,4),
    ADD COLUMN foreign_withholding_tax   NUMERIC(18,2),
    ADD COLUMN foreign_tax_rule_id       BIGINT,
    ADD CONSTRAINT fk_dividends_currency FOREIGN KEY (currency_code) REFERENCES currencies(code),
    ADD CONSTRAINT fk_dividends_foreign_tax_rule FOREIGN KEY (foreign_tax_rule_id) REFERENCES foreign_tax_rules(id),
    ADD CONSTRAINT ck_dividends_fx CHECK ((
        ((currency_code = 'BRL') AND (fx_rate = 1) AND (original_amount_per_share IS NULL) AND (original_total_amount IS NULL)
            AND (foreign_withholding_rate IS NULL) AND (foreign_withholding_tax IS NULL) AND (foreign_tax_rule_id IS NULL))
        OR ((currency_code <> 'BRL') AND (fx_rate > 0) AND (original_amount_per_share IS NOT NULL))
    ));

-- -----------------------------------------------------------------------------
-- Preço Teto: a moeda do lucro passa a apontar para o cadastro (D9), sem lista fixa.
-- -----------------------------------------------------------------------------
ALTER TABLE ceiling_price_quarters
    DROP CONSTRAINT ck_ceiling_price_quarters_currency,
    ADD CONSTRAINT fk_ceiling_price_quarters_currency FOREIGN KEY (currency) REFERENCES currencies(code);

-- -----------------------------------------------------------------------------
-- Tarefa do câmbio (10.3): 13:30, recuperação na subida, no máximo uma rodada automática por dia.
-- -----------------------------------------------------------------------------
INSERT INTO task_types (type, description, service, enabled, stoppable) VALUES
    ('EXCHANGE_RATES', 'Câmbio das moedas cadastradas (PTAX do Banco Central, boletim de fechamento, compra e venda). Sem período: rodada incremental com janela de revisão. No máximo uma rodada automática por dia (agendada ou subida); o Run manual roda sempre.', 'ADM_API', true, true);

INSERT INTO task_type_attributes (task_type_id, att_name, att_value, value_type, required, description, position)
SELECT id, 'CURRENCY_CODES', NULL, 'LIST', false, 'Códigos separados por vírgula (ex.: USD). Vazio = todas as moedas ativas com fonte de câmbio.', 1
FROM task_types WHERE type = 'EXCHANGE_RATES';
INSERT INTO task_type_attributes (task_type_id, att_name, att_value, value_type, required, description, position)
SELECT id, 'FROM_DATE', NULL, 'DATE', false, 'Primeira data. Vazio = rodada incremental (do último boletim gravado, com janela de revisão; moeda sem série começa em 2010).', 2
FROM task_types WHERE type = 'EXCHANGE_RATES';

INSERT INTO tasks (name, description, cron, enabled, task_type_id, run_on_startup)
SELECT 'EXCHANGE_RATES', NULL, '0 30 13 * * *', true, id, true FROM task_types WHERE type = 'EXCHANGE_RATES';

INSERT INTO task_attributes (task_id, task_type_attribute_id, att_value)
SELECT t.id, a.id, NULL
FROM tasks t JOIN task_type_attributes a ON a.task_type_id = t.task_type_id
WHERE t.name = 'EXCHANGE_RATES';
