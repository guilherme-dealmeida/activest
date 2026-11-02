-- Preço diário dos ativos internacionais vivos pelo Massive grouped daily
-- (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md, D2, D3, D4, I6 e I7 — 01/11/2026).
--
-- Fonte MASSIVE: os três tipos do exterior saem do MANUAL; o fechamento entra em asset_price_history com
-- source = MASSIVE. Tarefa FOREIGN_DAILY_QUOTES às 03:00 de terça a sábado (o plano gratuito libera o pregão logo
-- depois da meia-noite de Nova York), com recuperação na subida e no máximo uma rodada automática por dia.

-- -----------------------------------------------------------------------------
-- Fonte MASSIVE
-- -----------------------------------------------------------------------------
ALTER TABLE asset_types DROP CONSTRAINT ck_asset_types_price_source;
ALTER TABLE asset_types ADD CONSTRAINT ck_asset_types_price_source
    CHECK (price_source IN ('BRAPI', 'COINGECKO', 'TESOURO', 'MANUAL', 'CVM', 'MASSIVE'));
UPDATE asset_types SET price_source = 'MASSIVE' WHERE name IN ('STOCK', 'REIT', 'ETF_INTERNACIONAL');

ALTER TABLE asset_price_history DROP CONSTRAINT ck_price_history_source;
ALTER TABLE asset_price_history ADD CONSTRAINT ck_price_history_source
    CHECK (source IN ('BRAPI', 'COINGECKO', 'B3_COTAHIST', 'TESOURO', 'BINANCE', 'CVM', 'MASSIVE'));

-- -----------------------------------------------------------------------------
-- Tarefa FOREIGN_DAILY_QUOTES
-- -----------------------------------------------------------------------------
INSERT INTO task_types (type, description, service, enabled, stoppable) VALUES
    ('FOREIGN_DAILY_QUOTES', 'Preço diário dos ativos internacionais vivos (Stock, REIT e ETF internacional) pelo Massive grouped daily: uma chamada por pregão com o fechamento de todos os papéis dos EUA. Sem período: do pregão seguinte ao último gravado até ontem (até 31 dias). Grava o fechamento no histórico e o preço atual; o arquivo do dia fica em massive/grouped.', 'ADM_API', true, true);

INSERT INTO task_type_attributes (task_type_id, att_name, att_value, value_type, required, description, position)
SELECT id, v.att_name, v.att_value, v.value_type, v.required, v.description, v.position
FROM task_types,
     (VALUES ('FROM_DATE', NULL, 'DATE', false, 'Primeiro pregão (dentro dos 2 anos do plano gratuito). Vazio = do pregão seguinte ao último gravado até ontem.', 1),
             ('TO_DATE', NULL, 'DATE', false, 'Último pregão (até 31 dias depois do primeiro). Vazio = o próprio primeiro pregão.', 2))
         AS v(att_name, att_value, value_type, required, description, position)
WHERE type = 'FOREIGN_DAILY_QUOTES';

INSERT INTO tasks (name, description, cron, enabled, task_type_id, run_on_startup)
SELECT 'FOREIGN_DAILY_QUOTES', NULL, '0 0 3 * * TUE-SAT', true, id, true FROM task_types WHERE type = 'FOREIGN_DAILY_QUOTES';

INSERT INTO task_attributes (task_id, task_type_attribute_id, att_value)
SELECT t.id, a.id, a.att_value
FROM tasks t JOIN task_type_attributes a ON a.task_type_id = t.task_type_id
WHERE t.name = 'FOREIGN_DAILY_QUOTES';
