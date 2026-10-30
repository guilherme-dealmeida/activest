-- Tarefa do catálogo vivo dos EUA (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, 11.4, P3 e D12 — etapa F4, 29/10/2026).
--
-- FOREIGN_CATALOG: lista da bolsa (Nasdaq Trader) → tipo pelo OpenFIGI → categoria (BDR, SIC, Nareit, palavras do ETF)
-- → identidade pelo FIGI (renomeação) → saída com prova (Form 25 ou 30 dias fora da lista). Às segundas, 06:30 (P3:
-- antes da conferência da B3, às 07:00). WRITE nasce falso: a rodada só grava a prévia da planilha de revisão (o
-- portão da D12); o catálogo só é gravado depois da curadoria, com WRITE=true.

INSERT INTO task_types (type, description, service, enabled, stoppable) VALUES
    ('FOREIGN_CATALOG', 'Catálogo vivo dos EUA (Stock, REIT e ETF internacional): lista da bolsa do Nasdaq Trader, tipo pelo OpenFIGI, categoria (BDR da mesma empresa, SIC da SEC, setor da Nareit, palavras do nome do ETF) e a curadoria da planilha. Renomeia pelo FIGI e inativa com prova (Form 25 na SEC, ou 30 dias fora da lista). Sempre grava a prévia levantamento/internacional/catalogo-previa.json; só altera o catálogo com WRITE.', 'ADM_API', true, true);

INSERT INTO task_type_attributes (task_type_id, att_name, att_value, value_type, required, description, position)
SELECT id, 'WRITE', 'false', 'BOOLEAN', true, 'Grava o catálogo (novos, renomeados, atualizados e inativados). Falso: só a prévia para a planilha de revisão.', 1
FROM task_types WHERE type = 'FOREIGN_CATALOG';

INSERT INTO tasks (name, description, cron, enabled, task_type_id, run_on_startup)
SELECT 'FOREIGN_CATALOG', NULL, '0 30 6 * * MON', true, id, false FROM task_types WHERE type = 'FOREIGN_CATALOG';

INSERT INTO task_attributes (task_id, task_type_attribute_id, att_value)
SELECT t.id, a.id, a.att_value
FROM tasks t JOIN task_type_attributes a ON a.task_type_id = t.task_type_id
WHERE t.name = 'FOREIGN_CATALOG';
