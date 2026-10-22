-- Tarefas (docs/planos/PLANO_TAREFAS.md, T5): o tipo confere o Stop (parada cooperativa, D8)? A tela de Tarefas e a
-- adm-api precisam saber também dos tipos da app-api, cujo handler roda em outro serviço: cada serviço grava aqui, na
-- subida, o que os seus handlers declaram (TaskHandler.stoppable). O seed já nasce com o valor certo.

ALTER TABLE task_types ADD COLUMN stoppable BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE task_types SET stoppable = TRUE
WHERE type IN ('B3_CLOSING', 'CRYPTO_PRICE_HISTORY', 'DIVIDENDS_SYNC', 'FNET_STATUS_SYNC', 'CVM_STATEMENTS_IMPORT',
               'B3_DAILY_QUOTES', 'B3_YEARLY_QUOTES', 'MARKET_INDICES', 'CVM_PAYMENT_DATES', 'ASSET_LOGOS',
               'FIXED_INCOME_ISSUERS', 'BOOTSTRAP', 'POSITIONS_REFRESH');
