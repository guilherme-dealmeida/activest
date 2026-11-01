-- Carga retroativa do preço dos ativos internacionais pelo Massive grouped daily
-- (docs/planos/PLANO_PRECOS_HISTORICOS_INTERNACIONAIS.md — 01/11/2026). Só Run: um pedido por pregão a 5 por minuto
-- (~1h45 para os 2 anos do plano gratuito); o retroativo até 2010 fica como pendência (fonte paga).

INSERT INTO task_types (type, description, service, enabled, stoppable) VALUES
    ('FOREIGN_HISTORY_QUOTES', 'Carga retroativa do preço dos ativos internacionais (Stock, REIT e ETF internacional, vivos e deslistados até a data de saída) pelo Massive grouped daily, um pedido por pregão. Sem período: dos 2 anos do plano gratuito até ontem. Dia fora do plano é pulado; dia já baixado é relido do arquivo em massive/grouped.', 'ADM_API', true, true);

INSERT INTO task_type_attributes (task_type_id, att_name, att_value, value_type, required, description, position)
SELECT id, v.att_name, v.att_value, v.value_type, v.required, v.description, v.position
FROM task_types,
     (VALUES ('FROM_DATE', NULL, 'DATE', false, 'Primeiro pregão (de 2010). Vazio = 2 anos atrás, o limite do plano gratuito.', 1),
             ('TO_DATE', NULL, 'DATE', false, 'Último pregão. Vazio = ontem.', 2))
         AS v(att_name, att_value, value_type, required, description, position)
WHERE type = 'FOREIGN_HISTORY_QUOTES';

INSERT INTO tasks (name, description, cron, enabled, task_type_id, run_on_startup)
SELECT 'FOREIGN_HISTORY_QUOTES', NULL, NULL, true, id, false FROM task_types WHERE type = 'FOREIGN_HISTORY_QUOTES';

INSERT INTO task_attributes (task_id, task_type_attribute_id, att_value)
SELECT t.id, a.id, a.att_value
FROM tasks t JOIN task_type_attributes a ON a.task_type_id = t.task_type_id
WHERE t.name = 'FOREIGN_HISTORY_QUOTES';
