-- Sincronizacao em segundo plano (PLANO_CRIPTO_BINANCE.md, F5).
--
-- A fila e a propria integration_runs: POST .../preview cria uma execucao
-- QUEUED e responde na hora; o IntegrationWorker pega a mais antiga com
-- FOR UPDATE SKIP LOCKED (uma por conexao por vez), busca na corretora e a
-- deixa AWAITING_CONFIRMATION — ou confirma sozinho, na rotina diaria, quando
-- nao ha nada para o usuario decidir.
--
-- balances  saldo da corretora no momento da busca (ativo -> quantidade),
--           para o aviso de divergencia da regra E3: posicao do PrimeVest x
--           saldo da corretora, por ativo.
ALTER TABLE integration_runs ADD COLUMN balances JSONB;

-- O worker procura QUEUED por ordem de chegada, e a regra "uma por conexao"
-- procura RUNNING da mesma conexao.
CREATE INDEX idx_integration_runs_status ON integration_runs (status, created_at);
