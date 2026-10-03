-- Proventos e eventos societarios vindos da B3 (PLANO_BRAPI.md, F5 e F6).
--
-- -----------------------------------------------------------------------------
-- DUAS CAMADAS: O QUE O PAPEL PAGOU x O QUE VOCE RECEBEU
-- -----------------------------------------------------------------------------
-- O provento e um fato DO PAPEL: PETR4 pagou R$ 0,4716 por acao com data-com
-- 21/08/2026 para todo mundo. O que muda de um usuario para outro e so quantas
-- cotas ele tinha naquela data. Por isso o fato fica uma vez no catalogo
-- (asset_cash_events, sem user_id, como assets) e cada posicao tem a sua conta
-- em dividends, que ja existia.
--
-- O dado externo e buscado uma vez por papel, nao por usuario; uma compra
-- retroativa so recalcula, sem nova chamada a B3.
--
-- Desdobramento, grupamento e bonificacao (asset_stock_events) nao geram linha
-- por usuario: entram no calculo da posicao (PositionCalculator), como um passo
-- a mais na linha do tempo das transacoes.


-- -----------------------------------------------------------------------------
-- assets: identificadores que as fontes usam
-- -----------------------------------------------------------------------------
-- isin    a B3 devolve proventos por ISIN, nao por ticker (PETR3 e PETR4 vem na
--         mesma resposta do emissor PETR). Preenchida pela rotina.
-- cnpj    so FII: o FNET (historico de proventos alem de 12 meses) busca por
--         CNPJ do fundo. Preenchida pela rotina a partir do cadastro da CVM.
-- cash_history_loaded_at  quando o historico longo do ativo foi carregado. Nulo
--         = nunca; a rotina carrega uma vez, quando alguma posicao comeca antes
--         do evento mais antigo guardado.
ALTER TABLE assets ADD COLUMN isin                   VARCHAR(12);
ALTER TABLE assets ADD COLUMN cnpj                   VARCHAR(14);
ALTER TABLE assets ADD COLUMN cash_history_loaded_at TIMESTAMP;


-- -----------------------------------------------------------------------------
-- asset_cash_events — provento em dinheiro, por papel
-- -----------------------------------------------------------------------------
-- label e o rotulo como a fonte escreve ('JRS CAP PROPRIO'); type e o nosso
-- (JCP). type nulo = rotulo que ainda nao sabemos mapear: o evento fica gravado,
-- a rotina loga e nenhum provento e gerado ate o mapeamento existir.
--
-- value_per_share com 11 casas: e a precisao da B3 ('0,47156696000'), e o
-- historico longo divide por lote (1000 acoes nos anos 90).
--
-- payment_date e nula quando a fonte nao traz (o historico longo da B3 nao tem
-- data de pagamento).
--
-- A unicidade e o que impede a mesma noticia, vinda de duas fontes (B3 recente,
-- B3 historico, FNET), de virar dois proventos.
CREATE TABLE asset_cash_events (
    id                BIGSERIAL      PRIMARY KEY,
    asset_id          BIGINT         NOT NULL,
    type              VARCHAR(30),
    label             VARCHAR(60)    NOT NULL,
    data_com          DATE           NOT NULL,
    payment_date      DATE,
    value_per_share   NUMERIC(24,11) NOT NULL,
    approved_on       DATE,
    related_to        VARCHAR(100),
    isin              VARCHAR(12),
    income_tax_exempt BOOLEAN,
    source            VARCHAR(20)    NOT NULL,
    source_ref        VARCHAR(100),
    fetched_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_asset_cash_events_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT ck_asset_cash_events_type CHECK (type IN ('DIVIDENDO', 'JCP', 'RENDIMENTO', 'AMORTIZACAO', 'RESTITUICAO_CAPITAL')),
    CONSTRAINT ck_asset_cash_events_source CHECK (source IN ('B3', 'B3_HISTORICO', 'FNET')),
    CONSTRAINT uk_asset_cash_events UNIQUE NULLS NOT DISTINCT (asset_id, type, data_com, value_per_share)
);

CREATE INDEX idx_asset_cash_events_asset_date ON asset_cash_events (asset_id, data_com);


-- -----------------------------------------------------------------------------
-- asset_stock_events — desdobramento, grupamento e bonificacao, por papel
-- -----------------------------------------------------------------------------
-- factor como a B3 publica (conferido em casos reais, A12):
--   DESDOBRAMENTO  percentual de acoes novas: 100 = 1 vira 2 (PETR 2008),
--                  300 = 1 vira 4 (MGLU 2020), 900 = 1 vira 10 (MXRF 2017)
--   GRUPAMENTO     multiplicador: 0,1 = 10 viram 1 (MGLU 2024)
--   BONIFICACAO    percentual de acoes novas: 5 = 5 novas a cada 100 (MGLU 2025)
--
-- assigned_cost e o custo atribuido por acao bonificada. A B3 nao informa
-- (remarks vazio nos casos conferidos): nulo = custo zero provisorio (B27).
CREATE TABLE asset_stock_events (
    id            BIGSERIAL      PRIMARY KEY,
    asset_id      BIGINT         NOT NULL,
    type          VARCHAR(20)    NOT NULL,
    data_com      DATE           NOT NULL,
    factor        NUMERIC(24,11) NOT NULL,
    assigned_cost NUMERIC(18,8),
    approved_on   DATE,
    isin          VARCHAR(12),
    source        VARCHAR(20)    NOT NULL,
    fetched_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_asset_stock_events_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT ck_asset_stock_events_type CHECK (type IN ('DESDOBRAMENTO', 'GRUPAMENTO', 'BONIFICACAO')),
    CONSTRAINT ck_asset_stock_events_factor CHECK (factor > 0),
    CONSTRAINT uk_asset_stock_events UNIQUE (asset_id, type, data_com, factor)
);

CREATE INDEX idx_asset_stock_events_asset_date ON asset_stock_events (asset_id, data_com);


-- -----------------------------------------------------------------------------
-- dividends — a conta de cada posicao
-- -----------------------------------------------------------------------------
-- origin
--   AUTO    criado pelo DividendRecalculator a partir de um evento do catalogo x
--           cotas na data-com. Recalculado quando uma transacao muda.
--   MANUAL  lancado pelo usuario, para provento que nenhuma fonte tem. O
--           recalculador nao mexe nele, e ele nunca vai para o catalogo: o
--           catalogo e global, e dado digitado por um usuario apareceria para
--           todos.
-- As linhas que ja existirem ficam MANUAL: nao tem evento de catalogo por tras e
-- o recalculador nao teria como refaze-las.
ALTER TABLE dividends ADD COLUMN cash_event_id  BIGINT;
ALTER TABLE dividends ADD COLUMN shares_on_date NUMERIC(18,8);
ALTER TABLE dividends ADD COLUMN origin         VARCHAR(10) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE dividends ADD CONSTRAINT fk_dividends_cash_event
    FOREIGN KEY (cash_event_id) REFERENCES asset_cash_events (id);
ALTER TABLE dividends ADD CONSTRAINT ck_dividends_origin CHECK (origin IN ('AUTO', 'MANUAL'));
ALTER TABLE dividends ADD CONSTRAINT ck_dividends_auto_has_event CHECK (origin = 'MANUAL' OR cash_event_id IS NOT NULL);

-- Amortizacao (FII) e restituicao de capital (empresa) contam como provento e
-- reduzem o custo da posicao (B13).
ALTER TABLE dividends DROP CONSTRAINT ck_dividends_type;
ALTER TABLE dividends ADD CONSTRAINT ck_dividends_type
    CHECK (type IN ('DIVIDENDO', 'JCP', 'RENDIMENTO', 'AMORTIZACAO', 'RESTITUICAO_CAPITAL'));

-- Um evento do catalogo gera no maximo um provento por posicao.
CREATE UNIQUE INDEX uk_dividends_position_event
    ON dividends (portfolio_asset_id, cash_event_id)
    WHERE cash_event_id IS NOT NULL;

CREATE INDEX idx_dividends_cash_event ON dividends (cash_event_id);
