-- Proventos e desdobramentos dos ativos internacionais pelo Massive (docs/planos/PLANO_PROVENTOS_INTERNACIONAIS.md —
-- 01/11/2026). Fonte MASSIVE no catálogo de proventos; tarefa diária FOREIGN_DIVIDENDS (ter–sáb 03:30, janela de ex-date
-- de −30 a +90 dias, recuperação na subida, no máximo uma rodada automática por dia) e a carga FOREIGN_DIVIDENDS_HISTORY
-- (só Run, desde 2010).

ALTER TABLE asset_cash_events DROP CONSTRAINT ck_asset_cash_events_source;
ALTER TABLE asset_cash_events ADD CONSTRAINT ck_asset_cash_events_source
    CHECK (source IN ('B3', 'B3_HISTORICO', 'FNET', 'MASSIVE'));

INSERT INTO task_types (type, description, service, enabled, stoppable) VALUES
    ('FOREIGN_DIVIDENDS', 'Proventos e desdobramentos dos ativos internacionais (Stock, REIT e ETF internacional) pelo Massive: os meses da janela de ex-date de 30 dias atrás a 90 dias à frente, perguntando de novo (anúncios e correções). Só os tickers do catálogo, em dólar; data-com = último pregão antes da ex-date. Os meses ficam em massive/dividends e massive/splits.', 'ADM_API', true, true),
    ('FOREIGN_DIVIDENDS_HISTORY', 'Carga dos proventos e desdobramentos dos ativos internacionais pelo Massive, mês a mês, de FROM_DATE a TO_DATE. Mês encerrado e guardado em massive/dividends (ou splits) é relido do arquivo.', 'ADM_API', true, true);

INSERT INTO task_type_attributes (task_type_id, att_name, att_value, value_type, required, description, position)
SELECT id, v.att_name, v.att_value, v.value_type, v.required, v.description, v.position
FROM task_types,
     (VALUES ('FROM_DATE', '2010-01-01', 'DATE', false, 'Primeiro mês (a data vale pelo mês). Padrão: 2010-01-01.', 1),
             ('TO_DATE', NULL, 'DATE', false, 'Último mês. Vazio = 90 dias à frente (os anunciados).', 2))
         AS v(att_name, att_value, value_type, required, description, position)
WHERE type = 'FOREIGN_DIVIDENDS_HISTORY';

INSERT INTO tasks (name, description, cron, enabled, task_type_id, run_on_startup)
SELECT 'FOREIGN_DIVIDENDS', NULL, '0 30 3 * * TUE-SAT', true, id, true FROM task_types WHERE type = 'FOREIGN_DIVIDENDS';
INSERT INTO tasks (name, description, cron, enabled, task_type_id, run_on_startup)
SELECT 'FOREIGN_DIVIDENDS_HISTORY', NULL, NULL, true, id, false FROM task_types WHERE type = 'FOREIGN_DIVIDENDS_HISTORY';

INSERT INTO task_attributes (task_id, task_type_attribute_id, att_value)
SELECT t.id, a.id, a.att_value
FROM tasks t JOIN task_type_attributes a ON a.task_type_id = t.task_type_id
WHERE t.name = 'FOREIGN_DIVIDENDS_HISTORY';
