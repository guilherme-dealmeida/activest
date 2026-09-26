-- Confirmacao da importacao (PLANO_CRIPTO_BINANCE.md, F4).
--
-- A pre-visualizacao passa a ser guardada: POST .../preview cria uma execucao
-- (integration_runs, AWAITING_CONFIRMATION) com TODOS os movimentos que a
-- fonte devolveu, e a confirmacao grava a partir dela — sem buscar na
-- corretora de novo (a primeira busca leva mais de um minuto) e sem o risco de
-- confirmar algo diferente do que o usuario viu.

-- integration_run_items deixa de guardar so o que nao entrou: guarda o lote
-- inteiro. raw passa a ser o movimento normalizado (ImportedMovement em JSON,
-- que carrega o bruto da fonte dentro), para a confirmacao reaplicar as regras.
ALTER TABLE integration_run_items DROP CONSTRAINT ck_integration_run_items_outcome;
ALTER TABLE integration_run_items ADD CONSTRAINT ck_integration_run_items_outcome
    CHECK (outcome IN ('NEW', 'EXISTING', 'EXCLUDED', 'UNMAPPED', 'POSSIBLE_DUPLICATE', 'FAILED'));

-- duplicate_of    o lancamento MANUAL que parece ser esta mesma operacao (6.6).
--                 SET NULL: se o usuario apagar o manual antes de confirmar, a
--                 duvida some junto.
-- resolution      o que a confirmacao fez com o item:
--                 IMPORTED   virou transacao
--                 REPLACED   virou transacao e o manual duplicado saiu
--                 KEPT_BOTH  virou transacao e o manual ficou
--                 SKIPPED    nao entrou (usuario ignorou a duplicata, ou ja
--                            tinha sido importado/excluido nesse meio tempo)
-- transaction_id  a transacao criada, quando houve
ALTER TABLE integration_run_items ADD COLUMN duplicate_of   UUID;
ALTER TABLE integration_run_items ADD COLUMN resolution     VARCHAR(20);
ALTER TABLE integration_run_items ADD COLUMN transaction_id UUID;
ALTER TABLE integration_run_items ADD CONSTRAINT fk_integration_run_items_duplicate
    FOREIGN KEY (duplicate_of) REFERENCES transactions (id) ON DELETE SET NULL;
ALTER TABLE integration_run_items ADD CONSTRAINT fk_integration_run_items_transaction
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL;
ALTER TABLE integration_run_items ADD CONSTRAINT ck_integration_run_items_resolution
    CHECK (resolution IS NULL OR resolution IN ('IMPORTED', 'REPLACED', 'KEPT_BOTH', 'SKIPPED'));

-- next_cursors    onde cada fluxo vai parar se a execucao for confirmada. So
--                 vira integration_cursors na confirmacao: avancar antes faria
--                 uma operacao vista e nao confirmada nunca mais voltar.
-- fiat_deposited  soma dos depositos em reais do lote (conferencia, 4.6).
ALTER TABLE integration_runs ADD COLUMN next_cursors   JSONB;
ALTER TABLE integration_runs ADD COLUMN fiat_deposited NUMERIC(18,2);
