-- Tarefa dos deslistados dos EUA desde 2010 (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, C6, D2 e 11.4 — etapa F5,
-- 29/10/2026). Só Run manual (e a carga inicial): os Form 25 novos entram pela FOREIGN_CATALOG. WRITE nasce falso: a
-- rodada só grava a prévia da planilha dos deslistados; com WRITE=true cadastra os papéis como inativos.

INSERT INTO task_types (type, description, service, enabled, stoppable) VALUES
    ('FOREIGN_DELISTED', 'Deslistados dos EUA (Stock, REIT e ETF internacional) de FROM_YEAR a TO_YEAR: Form 25 e 25-NSE do índice da SEC, só as classes de capital nas bolsas da D1; ticker pelo XBRL (dei:TradingSymbol), pelo texto dos relatórios, pelo próprio Form 25 (fundos), pelas séries da SEC ou pela Nareit; tipo e categoria pelas regras do catálogo vivo e a curadoria da planilha. Sempre grava a prévia levantamento/internacional/deslistados-previa.json; com WRITE cadastra os papéis como inativos, com a data de saída.', 'ADM_API', true, true);

INSERT INTO task_type_attributes (task_type_id, att_name, att_value, value_type, required, description, position)
SELECT id, v.att_name, v.att_value, v.value_type, v.required, v.description, v.position
FROM task_types,
     (VALUES ('FROM_YEAR', '2010', 'YEAR', false, 'Primeiro ano dos Form 25 (de 2010).', 1),
             ('TO_YEAR', NULL, 'YEAR', false, 'Último ano. Vazio = o ano corrente.', 2),
             ('WRITE', 'false', 'BOOLEAN', true, 'Cadastra os deslistados com ticker (inativos). Falso: só a prévia para a planilha.', 3))
         AS v(att_name, att_value, value_type, required, description, position)
WHERE type = 'FOREIGN_DELISTED';

INSERT INTO tasks (name, description, cron, enabled, task_type_id, run_on_startup)
SELECT 'FOREIGN_DELISTED', NULL, NULL, true, id, false FROM task_types WHERE type = 'FOREIGN_DELISTED';

INSERT INTO task_attributes (task_id, task_type_attribute_id, att_value)
SELECT t.id, a.id, a.att_value
FROM tasks t JOIN task_type_attributes a ON a.task_type_id = t.task_type_id
WHERE t.name = 'FOREIGN_DELISTED';
