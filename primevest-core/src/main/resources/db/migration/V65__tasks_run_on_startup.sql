-- Tarefas (docs/planos/PLANO_TAREFAS.md, D37 e D38, 22/10/2026): a tarefa diz se roda na subida do serviço
-- (run_on_startup). Na subida, cada tarefa marcada, ativa, que ainda não teve rodada no dia, roda uma vez (origem
-- STARTUP) — se o handler disser que há o que recuperar. O seed liga as que já tinham recuperação na subida.

ALTER TABLE tasks ADD COLUMN run_on_startup BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tasks t SET run_on_startup = TRUE
FROM task_types y
WHERE y.id = t.task_type_id
  AND y.type IN ('B3_CLOSING', 'CRYPTO_CLOSING', 'TREASURY_PRICES', 'B3_DAILY_QUOTES', 'MARKET_INDICES',
                 'DAILY_OPENING');
