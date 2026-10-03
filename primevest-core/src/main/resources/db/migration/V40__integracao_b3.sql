-- Integracao com a B3 (docs/planos/PLANO_INTEGRACAO_B3.md, F2 — G7 e G8).
--
-- So duas regras mudam; nenhuma tabela e nenhuma coluna nova (principio P2):
--   provider: a B3 entra como fonte, ao lado da Binance;
--   status:   PENDING_AUTHORIZATION — o usuario informou o CPF, mas ainda nao autorizou o
--             PrimeVest na Area do Investidor da B3 (opt-in). A credencial (o CPF, cifrado)
--             fica em integration_credentials, como a chave da Binance.
ALTER TABLE integration_connections DROP CONSTRAINT ck_integration_connections_provider;
ALTER TABLE integration_connections ADD CONSTRAINT ck_integration_connections_provider
    CHECK (provider IN ('BINANCE', 'B3'));

ALTER TABLE integration_connections DROP CONSTRAINT ck_integration_connections_status;
ALTER TABLE integration_connections ADD CONSTRAINT ck_integration_connections_status
    CHECK (status IN ('ACTIVE', 'PENDING_AUTHORIZATION', 'INVALID_CREDENTIALS', 'PERMISSION_TOO_BROAD', 'REVOKED'));
