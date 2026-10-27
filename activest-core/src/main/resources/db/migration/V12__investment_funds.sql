-- Fundos de investimento da CVM no catálogo (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, seção 11.1, etapa F1).
--
-- O tipo FUNDO_INVESTIMENTO (id 6) já existia vazio e MANUAL. Daqui em diante:
--   * a cota diária vem da CVM (informe diário): fonte CVM no tipo e no histórico de preço;
--   * o fundo é um ativo do catálogo criado pela integração (tarefa FUND_QUOTES), não por seed: esta migration não
--     cadastra nenhum fundo, só a estrutura;
--   * ticker = CNPJ sem pontuação (14 caracteres, alfanumérico desde 07/2026) ou CNPJ-ID_SUBCLASSE; nunca exibido —
--     a tela mostra investment_funds.short_name (D3);
--   * categorias pelo 1º nível ANBIMA (D18);
--   * imposto de fundo por regime na tabela de IR/IOF da renda fixa (D17, D21; seed validado em 27/10/2026);
--   * come-cotas gravado em fund_tax_events (D22).

-- -----------------------------------------------------------------------------
-- Fonte CVM
-- -----------------------------------------------------------------------------
ALTER TABLE asset_types DROP CONSTRAINT ck_asset_types_price_source;
ALTER TABLE asset_types ADD CONSTRAINT ck_asset_types_price_source
    CHECK (price_source IN ('BRAPI', 'COINGECKO', 'TESOURO', 'MANUAL', 'CVM'));
UPDATE asset_types SET price_source = 'CVM' WHERE name = 'FUNDO_INVESTIMENTO';

ALTER TABLE asset_price_history DROP CONSTRAINT ck_price_history_source;
ALTER TABLE asset_price_history ADD CONSTRAINT ck_price_history_source
    CHECK (source IN ('BRAPI', 'COINGECKO', 'B3_COTAHIST', 'TESOURO', 'BINANCE', 'CVM'));

-- -----------------------------------------------------------------------------
-- Índices em cópia (D7): o único (uk_price_history_day, uk_assets_ticker) já cobre as mesmas colunas
-- -----------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_price_history_asset_date;
DROP INDEX IF EXISTS idx_assets_ticker;

-- -----------------------------------------------------------------------------
-- Categorias do tipo FUNDO_INVESTIMENTO (D18): 1º nível ANBIMA; sem ANBIMA, a classificação CVM; sem nenhuma, Outros
-- -----------------------------------------------------------------------------
INSERT INTO asset_categories (name, description, asset_type_id)
SELECT c.name, c.description, t.id
FROM asset_types t
CROSS JOIN (VALUES
    ('Multimercado', 'Fundos multimercado (ANBIMA nível 1: Multimercados)'),
    ('Renda Fixa', 'Fundos de renda fixa (ANBIMA nível 1: Renda Fixa)'),
    ('Previdência', 'Fundos de previdência — PGBL/VGBL (ANBIMA nível 1: Previdência)'),
    ('Ações', 'Fundos de ações, fechados de ações, mono ação e FMP-FGTS'),
    ('Cambial', 'Fundos cambiais'),
    ('Outros', 'Fundos sem classificação ANBIMA nem CVM')
) AS c(name, description)
WHERE t.name = 'FUNDO_INVESTIMENTO';

-- -----------------------------------------------------------------------------
-- investment_funds: cadastro CVM do fundo, 1:1 com assets
-- -----------------------------------------------------------------------------
-- assets.name = denominação social atual; short_name = rótulo das telas (denominação comercial vigente, senão a
-- social, abreviada — FundLabel); search_text = texto normalizado (sem acento, maiúsculas) de CNPJ e nomes atuais e
-- anteriores, para a busca com pg_trgm (D25). assets.active = "vivo" (C2, janela de 30 dias — D26).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE investment_funds (
    asset_id             BIGINT NOT NULL,
    cnpj                 VARCHAR(14) NOT NULL,
    subclass_id          VARCHAR(20),
    parent_asset_id      BIGINT,
    short_name           VARCHAR(160) NOT NULL,
    trade_name           VARCHAR(200),
    fund_kind            VARCHAR(20),
    cvm_classification   VARCHAR(40),
    anbima_classification VARCHAR(80),
    target_audience      VARCHAR(20),
    exclusive            BOOLEAN,
    open_ended           BOOLEAN,
    long_term_tax        BOOLEAN,
    manager_name         VARCHAR(200),
    manager_cnpj         VARCHAR(14),
    administrator_name   VARCHAR(200),
    administrator_cnpj   VARCHAR(14),
    cvm_status           VARCHAR(40),
    cvm_status_since     DATE,
    started_on           DATE,
    net_worth            NUMERIC(20,2),
    net_worth_date       DATE,
    last_quote_on        DATE,
    search_text          TEXT NOT NULL DEFAULT '',
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT investment_funds_pkey PRIMARY KEY (asset_id),
    CONSTRAINT fk_investment_funds_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE,
    CONSTRAINT fk_investment_funds_parent FOREIGN KEY (parent_asset_id) REFERENCES assets(id) ON DELETE SET NULL,
    CONSTRAINT ck_investment_funds_cnpj CHECK (cnpj ~ '^[0-9A-Z]{12}[0-9]{2}$')
);
CREATE UNIQUE INDEX uk_investment_funds_key ON investment_funds (cnpj, COALESCE(subclass_id, ''));
CREATE INDEX idx_investment_funds_search ON investment_funds USING gin (search_text gin_trgm_ops);
COMMENT ON TABLE investment_funds IS 'Cadastro CVM do fundo de investimento (1:1 com assets, tipo FUNDO_INVESTIMENTO). PLANO_FUNDOS_INVESTIMENTO.md';

-- -----------------------------------------------------------------------------
-- investment_fund_names: denominações sociais e comerciais, atuais e anteriores (cad_fi_hist), só para a busca
-- -----------------------------------------------------------------------------
CREATE TABLE investment_fund_names (
    id          BIGSERIAL,
    asset_id    BIGINT NOT NULL,
    name        VARCHAR(250) NOT NULL,
    kind        VARCHAR(10) NOT NULL,
    valid_from  DATE,
    valid_to    DATE,
    CONSTRAINT investment_fund_names_pkey PRIMARY KEY (id),
    CONSTRAINT fk_investment_fund_names_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE,
    CONSTRAINT ck_investment_fund_names_kind CHECK (kind IN ('LEGAL', 'TRADE'))
);
CREATE INDEX idx_investment_fund_names_asset ON investment_fund_names (asset_id);

-- -----------------------------------------------------------------------------
-- Imposto de fundo por regime (D17, D21; seção 11.3, validada pelo Guilherme em 27/10/2026)
-- -----------------------------------------------------------------------------
-- regime GENERAL = as linhas que já existiam (renda fixa e Tesouro; o IOF regressivo também vale para os fundos de
-- longo e curto prazo). tax CC = come-cotas (último dia útil de maio e de novembro, Lei 14.754/2023, art. 17).
-- A MP 1.303/2025 (17,5% único a partir de 2026) perdeu a validade em outubro de 2025: valem as alíquotas abaixo.
-- Previdência não tem linha: vale bruto (IR do plano, não do fundo — D21).
ALTER TABLE fixed_income_tax_rules ADD COLUMN regime VARCHAR(20) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE fixed_income_tax_rules ADD CONSTRAINT ck_fi_tax_rules_regime
    CHECK (regime IN ('GENERAL', 'FUND_LONG_TERM', 'FUND_SHORT_TERM', 'FUND_EQUITY'));
ALTER TABLE fixed_income_tax_rules DROP CONSTRAINT ck_fi_tax_rules_tax;
ALTER TABLE fixed_income_tax_rules ADD CONSTRAINT ck_fi_tax_rules_tax CHECK (tax IN ('IR', 'IOF', 'CC'));

INSERT INTO fixed_income_tax_rules (tax, regime, asset_id, days_from, days_to, rate, valid_from, valid_to, legal_basis) VALUES
    ('IR', 'FUND_LONG_TERM', NULL, 0, 180, 0.2250, NULL, NULL, 'Lei 11.033/2004, art. 1º; Lei 14.754/2023'),
    ('IR', 'FUND_LONG_TERM', NULL, 181, 360, 0.2000, NULL, NULL, 'Lei 11.033/2004, art. 1º; Lei 14.754/2023'),
    ('IR', 'FUND_LONG_TERM', NULL, 361, 720, 0.1750, NULL, NULL, 'Lei 11.033/2004, art. 1º; Lei 14.754/2023'),
    ('IR', 'FUND_LONG_TERM', NULL, 721, NULL, 0.1500, NULL, NULL, 'Lei 11.033/2004, art. 1º; Lei 14.754/2023'),
    ('CC', 'FUND_LONG_TERM', NULL, 0, NULL, 0.1500, NULL, NULL, 'Lei 14.754/2023, art. 17'),
    ('IR', 'FUND_SHORT_TERM', NULL, 0, 180, 0.2250, NULL, NULL, 'Lei 11.033/2004, art. 1º, § 3º; Lei 14.754/2023'),
    ('IR', 'FUND_SHORT_TERM', NULL, 181, NULL, 0.2000, NULL, NULL, 'Lei 11.033/2004, art. 1º, § 3º; Lei 14.754/2023'),
    ('CC', 'FUND_SHORT_TERM', NULL, 0, NULL, 0.2000, NULL, NULL, 'Lei 14.754/2023, art. 17'),
    ('IR', 'FUND_EQUITY', NULL, 0, NULL, 0.1500, NULL, NULL, 'Lei 11.033/2004, art. 1º, § 3º, II');

-- -----------------------------------------------------------------------------
-- fund_tax_events: come-cotas gravado por lote (D22), regerado quando transação ou cota muda
-- -----------------------------------------------------------------------------
CREATE TABLE fund_tax_events (
    id                       BIGSERIAL,
    user_id                  UUID NOT NULL,
    asset_id                 BIGINT NOT NULL,
    lot_transaction_id       UUID NOT NULL,
    event_date               DATE NOT NULL,
    kind                     VARCHAR(20) NOT NULL DEFAULT 'COME_COTAS',
    quote                    NUMERIC(18,8) NOT NULL,
    base_quote               NUMERIC(18,8) NOT NULL,
    shares_before            NUMERIC(18,8) NOT NULL,
    shares_withheld          NUMERIC(18,8) NOT NULL,
    tax_amount               NUMERIC(18,2) NOT NULL,
    rate                     NUMERIC(6,4) NOT NULL,
    fixed_income_tax_rule_id BIGINT,
    created_at               TIMESTAMP NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fund_tax_events_pkey PRIMARY KEY (id),
    CONSTRAINT fk_fund_tax_events_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_fund_tax_events_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT fk_fund_tax_events_lot FOREIGN KEY (lot_transaction_id) REFERENCES transactions(id) ON DELETE CASCADE,
    CONSTRAINT fk_fund_tax_events_rule FOREIGN KEY (fixed_income_tax_rule_id) REFERENCES fixed_income_tax_rules(id),
    CONSTRAINT ck_fund_tax_events_kind CHECK (kind IN ('COME_COTAS')),
    CONSTRAINT uk_fund_tax_events_lot_date UNIQUE (lot_transaction_id, event_date, kind)
);
CREATE INDEX idx_fund_tax_events_user_asset ON fund_tax_events (user_id, asset_id);

-- -----------------------------------------------------------------------------
-- Tarefa FUND_QUOTES (seção 11.4, D8): cadastro + cota diária da CVM, uma rodada automática por dia
-- -----------------------------------------------------------------------------
INSERT INTO task_types (type, description, service, enabled, stoppable) VALUES
    ('FUND_QUOTES', 'Fundos de investimento da CVM: cadastro (registro_fundo_classe e cad_fi_hist) e cota diária (informe diário do mês corrente e dos 12 anteriores). Grava a última cota de todos os fundos vivos e a série dos fundos em carteira. No máximo uma rodada automática por dia (agendada ou subida); o Run manual roda sempre.', 'ADM_API', true, true);

INSERT INTO task_type_attributes (task_type_id, att_name, att_value, value_type, required, description, position)
SELECT id, 'FROM_MONTH', NULL, 'DATE', false, 'Primeiro mês do informe a rebaixar (qualquer dia do mês). Vazio = mês corrente e os 12 anteriores.', 1
FROM task_types WHERE type = 'FUND_QUOTES';

INSERT INTO tasks (name, description, cron, enabled, task_type_id, run_on_startup)
SELECT 'FUND_QUOTES', NULL, '0 40 7 * * *', true, id, true FROM task_types WHERE type = 'FUND_QUOTES';

INSERT INTO task_attributes (task_id, task_type_attribute_id, att_value)
SELECT t.id, a.id, NULL
FROM tasks t JOIN task_type_attributes a ON a.task_type_id = t.task_type_id
WHERE t.name = 'FUND_QUOTES';
