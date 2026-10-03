-- Integracoes: conexoes de cada usuario com fontes externas (corretoras cripto
-- primeiro) e a importacao das operacoes delas. PLANO_CRIPTO_BINANCE.md, 6.3 e 6.10.
--
-- Multiusuario desde o inicio: a conexao e a credencial pertencem ao usuario,
-- nunca ao servidor. A credencial fica cifrada (AES-256-GCM) pela aplicacao,
-- com a chave-mestra fora do banco (PRIMEVEST_CREDENTIALS_KEY): o banco sozinho
-- nao abre nada.
--
-- Ordem das FKs: integration_connections -> integration_credentials,
-- integration_cursors, integration_runs -> integration_run_items,
-- integration_crypto_exclusions; e por ultimo as colunas novas de transactions.

-- ---------------------------------------------------------------------------
-- Conexao: uma por conta na fonte. Um usuario pode ter varias (duas contas da
-- Binance, por exemplo), diferenciadas pelo apelido.
--
-- provider: so BINANCE por enquanto. Fonte nova (outra corretora, arquivo,
-- Open Finance) entra por migration, junto com o conector dela.
--
-- status:
--   ACTIVE                credencial valida e so de leitura
--   INVALID_CREDENTIALS   a fonte recusou a chave (apagada, expirada, errada)
--   PERMISSION_TOO_BROAD  a chave negocia, saca ou transfere: sync bloqueado
--   REVOKED               o usuario desconectou; a credencial ja foi apagada
--
-- consent_accepted_at: quando o usuario aceitou o texto de consentimento
-- (o que e lido, para que, por quanto tempo). LGPD, 6.4 do plano.
-- ---------------------------------------------------------------------------
CREATE TABLE integration_connections (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL,
    provider            VARCHAR(30)  NOT NULL,
    label               VARCHAR(100) NOT NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    consent_accepted_at TIMESTAMP    NOT NULL,
    last_sync_at        TIMESTAMP,
    last_error          TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_integration_connections_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_integration_connections_label UNIQUE (user_id, label),
    CONSTRAINT ck_integration_connections_provider CHECK (provider IN ('BINANCE')),
    CONSTRAINT ck_integration_connections_status
        CHECK (status IN ('ACTIVE', 'INVALID_CREDENTIALS', 'PERMISSION_TOO_BROAD', 'REVOKED'))
);
CREATE INDEX idx_integration_connections_user ON integration_connections (user_id);

-- ---------------------------------------------------------------------------
-- Credencial: 1:1 com a conexao, e a unica tabela com segredo.
--
-- ciphertext  JSON da credencial ({"apiKey": ..., "apiSecret": ...}) cifrado
--             com AES-256-GCM; inclui a tag de autenticacao de 16 bytes.
-- iv          12 bytes, novo a cada cifragem. Nunca se repete para a mesma chave.
-- key_version qual chave-mestra cifrou: permite rotacionar sem perder o que ja
--             esta gravado (a antiga continua configurada so para ler).
-- fingerprint os ultimos 4 caracteres da chave de API, o unico pedaco que a tela
--             mostra ("****a7K2").
-- permissions JSON da ultima checagem de permissao na fonte (6.4).
--
-- A cifragem usa o id da conexao como dado associado (AAD): um ciphertext
-- copiado para outra linha nao decifra.
--
-- Revogar a conexao APAGA esta linha (nao ha soft delete de segredo).
-- ---------------------------------------------------------------------------
CREATE TABLE integration_credentials (
    connection_id UUID        PRIMARY KEY,
    ciphertext    BYTEA       NOT NULL,
    iv            BYTEA       NOT NULL,
    key_version   SMALLINT    NOT NULL,
    fingerprint   VARCHAR(8)  NOT NULL,
    permissions   JSONB,
    validated_at  TIMESTAMP,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_integration_credentials_connection
        FOREIGN KEY (connection_id) REFERENCES integration_connections (id) ON DELETE CASCADE,
    CONSTRAINT ck_integration_credentials_iv CHECK (octet_length(iv) = 12),
    CONSTRAINT ck_integration_credentials_key_version CHECK (key_version > 0)
);

-- ---------------------------------------------------------------------------
-- Cursor: onde cada fluxo da fonte parou, para a sincronizacao ser incremental
-- (a primeira busca o historico inteiro; as seguintes, so o que e novo).
-- stream e o fluxo na linguagem do conector: 'spot:BTCBRL', 'convert',
-- 'fiat_payments'. cursor_value e opaco para o banco (ultimo id ou instante).
-- ---------------------------------------------------------------------------
CREATE TABLE integration_cursors (
    connection_id UUID         NOT NULL,
    stream        VARCHAR(100) NOT NULL,
    cursor_value  VARCHAR(200) NOT NULL,
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_integration_cursors PRIMARY KEY (connection_id, stream),
    CONSTRAINT fk_integration_cursors_connection
        FOREIGN KEY (connection_id) REFERENCES integration_connections (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Execucao: cada sincronizacao, com o resultado que a tela mostra.
--
-- trigger_type  MANUAL (botao), SCHEDULED (rotina diaria), CONNECTION (a
--               primeira, disparada ao conectar)
-- status        QUEUED -> RUNNING -> AWAITING_CONFIRMATION (pre-visualizacao,
--               6.6/6.7) -> SUCCEEDED; ou FAILED / CANCELLED
-- contagens     importadas, ja existentes, nao mapeadas, possiveis duplicatas
-- ---------------------------------------------------------------------------
CREATE TABLE integration_runs (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id      UUID        NOT NULL,
    trigger_type       VARCHAR(20) NOT NULL,
    status             VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    started_at         TIMESTAMP,
    finished_at        TIMESTAMP,
    imported_count     INTEGER     NOT NULL DEFAULT 0,
    existing_count     INTEGER     NOT NULL DEFAULT 0,
    unmapped_count     INTEGER     NOT NULL DEFAULT 0,
    duplicate_count    INTEGER     NOT NULL DEFAULT 0,
    error              TEXT,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_integration_runs_connection
        FOREIGN KEY (connection_id) REFERENCES integration_connections (id) ON DELETE CASCADE,
    CONSTRAINT ck_integration_runs_trigger CHECK (trigger_type IN ('MANUAL', 'SCHEDULED', 'CONNECTION')),
    CONSTRAINT ck_integration_runs_status CHECK (status IN
        ('QUEUED', 'RUNNING', 'AWAITING_CONFIRMATION', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_integration_runs_counts CHECK (
        imported_count >= 0 AND existing_count >= 0 AND unmapped_count >= 0 AND duplicate_count >= 0)
);
CREATE INDEX idx_integration_runs_connection ON integration_runs (connection_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Itens da execucao: SO o que nao entrou direto — e o "nao mapeadas" do
-- levantamento, persistido. O que foi importado ja esta em transactions.
--
-- outcome  UNMAPPED            regra R6/R7/R8: sem valor em reais, ticker fora
--                              do catalogo, taxa em outro ativo (PENDENCIAS 15)
--          POSSIBLE_DUPLICATE  parece com um lancamento manual (6.6); aguarda
--                              a decisao do usuario
--          FAILED              erro ao gravar (regra de negocio recusou)
-- raw      o movimento como veio da fonte, para auditoria e reprocessamento
-- ---------------------------------------------------------------------------
CREATE TABLE integration_run_items (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id      UUID         NOT NULL,
    external_id VARCHAR(200) NOT NULL,
    outcome     VARCHAR(30)  NOT NULL,
    reason      VARCHAR(500),
    raw         JSONB        NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_integration_run_items_run FOREIGN KEY (run_id) REFERENCES integration_runs (id) ON DELETE CASCADE,
    CONSTRAINT uk_integration_run_items UNIQUE (run_id, external_id),
    CONSTRAINT ck_integration_run_items_outcome CHECK (outcome IN ('UNMAPPED', 'POSSIBLE_DUPLICATE', 'FAILED'))
);

-- ---------------------------------------------------------------------------
-- Exclusoes de cripto (D9, regra E2): operacao importada que o usuario
-- excluiu. A sincronizacao seguinte a encontra na corretora, consulta esta
-- tabela e nao recria. "Restaurar" (E4) apaga a linha e a proxima
-- sincronizacao traz a operacao de volta.
--
-- Uma tabela por dominio: a renda fixa tera integration_fixed_income_exclusions
-- se o Open Finance vier a precisar.
--
-- source_snapshot: a operacao como estava ao ser excluida, para listar o que
-- foi excluido e para restaurar.
-- ---------------------------------------------------------------------------
CREATE TABLE integration_crypto_exclusions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id   UUID         NOT NULL,
    external_id     VARCHAR(200) NOT NULL,
    asset_id        BIGINT       NOT NULL,
    source_snapshot JSONB        NOT NULL,
    excluded_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_integration_crypto_exclusions_connection
        FOREIGN KEY (connection_id) REFERENCES integration_connections (id) ON DELETE CASCADE,
    CONSTRAINT fk_integration_crypto_exclusions_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT uk_integration_crypto_exclusions UNIQUE (connection_id, external_id)
);
CREATE INDEX idx_integration_crypto_exclusions_asset ON integration_crypto_exclusions (asset_id);

-- ---------------------------------------------------------------------------
-- transactions: origem e rastreio da importacao (5.1 item 2, 5.2 item 3, 6.10).
--
-- origin          MANUAL (digitada) ou AUTO (importada), como dividends.origin
-- external_id     id estavel da operacao na fonte (R5):
--                 'binance:spot:BTCBRL:<orderId>', 'binance:convert:<quoteId>'
-- connection_id   de qual conexao veio. SET NULL: se a conexao for apagada e o
--                 usuario escolher manter as transacoes (6.4), elas ficam como
--                 importadas, so sem vinculo
-- executed_at     instante exato na fonte; transaction_date continua sendo a
--                 data de negocio (fuso de Brasilia, R3). Nulo nas manuais
-- edited_at       o usuario editou uma importada: a sincronizacao nao toca mais
--                 nela (E1). Nulo = identica a fonte
-- source_snapshot os valores como vieram da fonte, para "restaurar" (E4)
--
-- Importada nasce com external_id e source_snapshot; manual, sem nenhum dos dois.
-- A unicidade (connection_id, external_id) e o que torna a reimportacao
-- idempotente (P4).
-- ---------------------------------------------------------------------------
ALTER TABLE transactions ADD COLUMN origin          VARCHAR(10)  NOT NULL DEFAULT 'MANUAL';
ALTER TABLE transactions ADD COLUMN external_id     VARCHAR(200);
ALTER TABLE transactions ADD COLUMN connection_id   UUID;
ALTER TABLE transactions ADD COLUMN executed_at     TIMESTAMP;
ALTER TABLE transactions ADD COLUMN edited_at       TIMESTAMP;
ALTER TABLE transactions ADD COLUMN source_snapshot JSONB;

ALTER TABLE transactions ADD CONSTRAINT fk_transactions_connection
    FOREIGN KEY (connection_id) REFERENCES integration_connections (id) ON DELETE SET NULL;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_origin CHECK (
    (origin = 'MANUAL' AND external_id IS NULL AND source_snapshot IS NULL AND edited_at IS NULL)
    OR (origin = 'AUTO' AND external_id IS NOT NULL AND source_snapshot IS NOT NULL)
);
CREATE UNIQUE INDEX uk_transactions_external
    ON transactions (connection_id, external_id) WHERE connection_id IS NOT NULL;
