-- =============================================================================
-- Activest — schema completo (V1).
--
-- Consolidado em 2026-10-23 na renomeacao PrimeVest -> Activest (docs/planos/PLANO_RENOMEACAO_ACTIVEST.md, 4.2), a
-- partir do banco que as migrations V1–V67 do PrimeVest produziam (carga/scripts/migrations/consolidate.mjs).
-- Cada tabela nasce pronta: colunas, defaults, constraints e indices finais, sem ALTER posterior. A historia de
-- cada tabela (as migrations que a criaram e mudaram) esta no repositorio primevest; cada bloco abaixo cita as
-- versoes de origem.
--
-- A ordem das tabelas e a das chaves estrangeiras. Colunas: chave primaria, dados, chaves estrangeiras e
-- auditoria no fim.
--
-- Convencoes (herdadas):
--   - UUID nas tabelas de dados do usuario; BIGSERIAL no catalogo.
--   - Quantidades e precos unitarios em NUMERIC(18,8) (cripto); valores monetarios agregados em NUMERIC(18,2).
--   - Toda coluna de data de auditoria e NOT NULL DEFAULT NOW(): o preenchimento no Java e manual, e um INSERT
--     por SQL direto (seed, migration, psql) depende do default.
--   - Campo novo obrigatorio em tabela existente (migrations futuras) nasce com o default informado (REGRAS 8.1).
-- =============================================================================


-- -----------------------------------------------------------------------------
-- users
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V9__seed_dev_user, V41__cadastro_usuario, V67__constraint_names_english
--
-- [V1] Autenticação por email/senha (password_hash) ou Google (google_id).
CREATE TABLE users (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    name                VARCHAR(255) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255),
    google_id           VARCHAR(255),
    avatar_url          VARCHAR(512),
    is_admin            BOOLEAN NOT NULL DEFAULT FALSE,
    phone               VARCHAR(20),
    cpf_ciphertext      BYTEA,
    cpf_iv              BYTEA,
    cpf_key_version     SMALLINT,
    cpf_hash            VARCHAR(64),
    email_verified_at   TIMESTAMP,
    password_changed_at TIMESTAMP,
    terms_accepted_at   TIMESTAMP,
    terms_version       VARCHAR(30),
    theme               VARCHAR(10) NOT NULL DEFAULT 'DARK',
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_google_id UNIQUE (google_id),
    CONSTRAINT ck_users_cpf_complete CHECK ((((cpf_hash IS NULL) AND (cpf_ciphertext IS NULL) AND (cpf_iv IS NULL) AND (cpf_key_version IS NULL)) OR ((cpf_hash IS NOT NULL) AND (cpf_ciphertext IS NOT NULL) AND (cpf_iv IS NOT NULL) AND (cpf_key_version IS NOT NULL)))),
    CONSTRAINT ck_users_theme CHECK ((theme IN ('DARK', 'LIGHT')))
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_google_id ON users (google_id);
CREATE UNIQUE INDEX uk_users_cpf ON users (cpf_hash) WHERE (cpf_hash IS NOT NULL);
CREATE UNIQUE INDEX uk_users_email_lower ON users (lower((email)::text));
CREATE UNIQUE INDEX uk_users_phone ON users (phone) WHERE (phone IS NOT NULL);


-- -----------------------------------------------------------------------------
-- asset_types
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V2__seed_asset_types, V44__tesouro_preco_oficial
--
-- [V1] Tipos de ativo (Ações, FII, Tesouro Direto...). Tabela de referência.
-- price_source diz de ONDE vem a cotação daquele tipo. Existe para que a
-- escolha do provedor seja dado, e não comparação de string no código: antes
-- o scheduler fazia "CRYPTO".equals(name), o que quebrava silenciosamente a
-- cada renomeação ou tipo novo.
--   BRAPI      cotação da B3
--   COINGECKO  criptomoedas
--   MANUAL     sem provedor automático (renda fixa, fundos, ativos dos EUA
--              enquanto não houver cliente para eles)
-- fixed_income marca os tipos que têm remuneração contratada — os que abrem o
-- quadro do título no lançamento e geram uma linha em fixed_income_notes. É
-- coluna, e não lista de nomes no código, pela mesma razão de price_source: a
-- tela e o service só perguntam à flag.
CREATE TABLE asset_types (
    id           BIGSERIAL,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(255),
    color_hex    VARCHAR(255),
    price_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    fixed_income BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT asset_types_pkey PRIMARY KEY (id),
    CONSTRAINT uk_asset_types_name UNIQUE (name),
    CONSTRAINT ck_asset_types_price_source CHECK ((price_source IN ('BRAPI', 'COINGECKO', 'TESOURO', 'MANUAL')))
);


-- -----------------------------------------------------------------------------
-- asset_categories
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V3__seed_asset_categories, V16__seed_etf_categories, V18__seed_assets_bdr, V19__seed_assets_cripto, V31__asset_categories_without_segment, V54__emissoras_renda_fixa
--
-- [V1] Categorias dentro de cada tipo de ativo (CDB, Tijolo, Papel...).
-- segment é o segundo nível, e o que ele significa depende do tipo:
--   FII    tipo de fundo > segmento
--   AÇÃO   setor B3 > segmento B3
--   demais nulo
--
-- [V54] Produto -> tipos de emissora e cobertura (secao 4.4, D2)
-- issuer_kinds: tipos aceitos no seletor de emissora (alem de NAO_LISTADA, sempre aceita). So renda fixa.
-- guaranteed: o produto esta entre os creditos garantidos — FGC: Regulamento do FGC (Anexo II a Resolucao CMN
-- 4.222/2013), art. 2o: II poupanca, III deposito a prazo (CDB, RDB), V letra de cambio, VII LCI, VIII LCA; DPGE
-- pela garantia especial do mesmo regulamento. FGCoop (cooperativas e bancos cooperativos, Resolucao CMN
-- 4.284/2013): deposito a prazo (RDC), poupanca, LCI, LCA, LC. Fora: LF, LIG, CCB, CRI, CRA, debentures e nota
-- comercial (conferido em 16/10/2026). O titulo e garantido se o produto e coberto E a emissora tem garantidor.
CREATE TABLE asset_categories (
    id            BIGSERIAL,
    name          VARCHAR(255) NOT NULL,
    description   VARCHAR(255),
    issuer_kinds  VARCHAR(120),
    guaranteed    BOOLEAN NOT NULL DEFAULT FALSE,
    asset_type_id BIGINT NOT NULL,
    CONSTRAINT asset_categories_pkey PRIMARY KEY (id),
    CONSTRAINT uk_asset_categories_scope UNIQUE (asset_type_id, name),
    CONSTRAINT fk_asset_categories_type FOREIGN KEY (asset_type_id) REFERENCES asset_types(id)
);
COMMENT ON TABLE asset_categories IS 'Categoria dentro do tipo de ativo (um nivel). Segmento de acao e FII fica no emissor (issuers).';

CREATE INDEX idx_asset_categories_type ON asset_categories (asset_type_id);


-- -----------------------------------------------------------------------------
-- portfolios
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V9__seed_dev_user
--
-- [V1] Carteira do usuário, uma por usuário.
-- NÃO guarda totais. total_invested, current_value e total_dividends existiram
-- e saíram: eram cópias do que as posições já dizem, e só ficavam certas
-- enquanto alguém lembrasse de recalculá-las. O resumo da carteira soma as
-- posições na leitura.
CREATE TABLE portfolios (
    id         UUID NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    user_id    UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT portfolios_pkey PRIMARY KEY (id),
    CONSTRAINT fk_portfolios_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_portfolios_user ON portfolios (user_id);


-- -----------------------------------------------------------------------------
-- strategies
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V10__drop_strategies_include_reservation
--
-- [V1] Percentual ideal por tipo de ativo, base do rebalanceamento.
-- Um registro por usuário e tipo.
CREATE TABLE strategies (
    id                UUID NOT NULL DEFAULT gen_random_uuid(),
    target_percentage NUMERIC(5,2) NOT NULL,
    user_id           UUID NOT NULL,
    asset_type_id     BIGINT NOT NULL,
    CONSTRAINT strategies_pkey PRIMARY KEY (id),
    CONSTRAINT uk_strategies_scope UNIQUE (user_id, asset_type_id),
    CONSTRAINT fk_strategies_type FOREIGN KEY (asset_type_id) REFERENCES asset_types(id),
    CONSTRAINT fk_strategies_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_strategies_percentage CHECK (((target_percentage >= (0)) AND (target_percentage <= (100))))
);

CREATE INDEX idx_strategies_user ON strategies (user_id);


-- -----------------------------------------------------------------------------
-- goals
-- -----------------------------------------------------------------------------
-- Origem: V1__schema
--
-- [V1] Metas de investimento do usuário.
CREATE TABLE goals (
    id             UUID NOT NULL DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL,
    target_amount  NUMERIC(18,2) NOT NULL,
    current_amount NUMERIC(18,2),
    target_date    DATE,
    status         VARCHAR(20) NOT NULL,
    user_id        UUID NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT goals_pkey PRIMARY KEY (id),
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_goals_status CHECK ((status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')))
);

CREATE INDEX idx_goals_user ON goals (user_id);


-- -----------------------------------------------------------------------------
-- strategy_reservations
-- -----------------------------------------------------------------------------
-- Origem: V11__strategy_reservations, V12__seed_dev_user_strategy_reservation
--
-- [V11] Estrategia da reserva de emergencia: quanto da carteira o usuario quer em
-- posicoes marcadas como reserva (portfolio_assets.is_reservation). O resto da
-- carteira e a geracao de renda.
-- Uma linha por usuario, sempre (regras A e B): nasce com o usuario, nunca e
-- apagada pela aplicacao. A unicidade em user_id e a regra A no banco, e nao
-- so no codigo.
-- type_reservation e numerico por especificacao do escopo: 1 = por
-- percentual, 2 = por valor. No Java ele e o enum ReservationType, lido por
-- um AttributeConverter — o resto do projeto grava enum como VARCHAR, e esta
-- e a excecao.
-- O alvo mora em uma das duas colunas, conforme o tipo, e a outra fica nula.
-- A checagem abaixo garante isso: sem ela as duas acumulariam o que sobrou de
-- quando o usuario trocou de tipo, e o calculo dependeria de qual delas
-- alguem leu.
CREATE TABLE strategy_reservations (
    id                UUID NOT NULL DEFAULT gen_random_uuid(),
    type_reservation  SMALLINT NOT NULL DEFAULT 1,
    target_percentage NUMERIC(5,2),
    target_value      NUMERIC(18,2),
    user_id           UUID NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT strategy_reservations_pkey PRIMARY KEY (id),
    CONSTRAINT uk_strategy_reservations_user UNIQUE (user_id),
    CONSTRAINT fk_strategy_reservations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_strategy_reservations_target CHECK ((((type_reservation = 1) AND (target_percentage IS NOT NULL) AND (target_percentage >= (0)) AND (target_percentage <= (100)) AND (target_value IS NULL)) OR ((type_reservation = 2) AND (target_value IS NOT NULL) AND (target_value >= (0)) AND (target_percentage IS NULL)))),
    CONSTRAINT ck_strategy_reservations_type CHECK (type_reservation IN (1, 2))
);


-- -----------------------------------------------------------------------------
-- withholding_tax_rules
-- -----------------------------------------------------------------------------
-- Origem: V23__dividend_withholding_tax
--
-- [V23] IR retido na fonte sobre proventos: bruto x liquido (PLANO_IR_PROVENTOS.md).
-- O QUE A LEI MUDA FICA AQUI; O JEITO DE CALCULAR FICA NO CODIGO
-- withholding_tax_rules guarda a aliquota de cada tipo de provento, por tipo de
-- ativo e por vigencia, com a base legal ao lado. O calculo (bruto e IR
-- truncados nos centavos, como a B3; qual data conta como credito) esta em
-- WithholdingTaxCalculator e nao muda com a lei.
-- Investidor pessoa fisica residente. Tipo de provento + tipo de ativo sem
-- linha vigente = "sem regra": aliquota e liquido nulos (ETF, BDR...).
-- base_date diz qual data do provento cai na vigencia:
--   CREDIT   data do credito ao acionista = aprovacao (approved_on); sem ela,
--            data-com; sem as duas, pagamento. E o fato gerador do JCP ("na
--            data do pagamento ou do credito", o que vier primeiro).
--   PAYMENT  data de pagamento; sem ela, data-com.
-- COMO MUDAR UMA ALIQUOTA (lei nova)
-- Uma migration nova, nunca UPDATE nesta:
--   1. fecha a linha vigente:   UPDATE withholding_tax_rules SET valid_to = <vespera>
--   2. insere a linha nova:     INSERT ... valid_from = <inicio da vigencia>
--   3. marca os proventos afetados para recalculo:
--        UPDATE dividends SET tax_checked_at = NULL WHERE type = '<tipo>' ...
-- Na subida seguinte, o DividendTaxVerifier recalcula so esses. Nenhuma
-- vigencia pode se sobrepor a outra do mesmo tipo de provento e de ativo
-- (WithholdingTaxRuleRepositoryTest confere).
CREATE TABLE withholding_tax_rules (
    id            BIGSERIAL,
    dividend_type VARCHAR(30) NOT NULL,
    rate          NUMERIC(6,4) NOT NULL,
    base_date     VARCHAR(10) NOT NULL,
    valid_from    DATE,
    valid_to      DATE,
    legal_basis   VARCHAR(200) NOT NULL,
    asset_type_id BIGINT NOT NULL,
    CONSTRAINT withholding_tax_rules_pkey PRIMARY KEY (id),
    CONSTRAINT fk_withholding_tax_rules_asset_type FOREIGN KEY (asset_type_id) REFERENCES asset_types(id),
    CONSTRAINT ck_withholding_tax_rules_base_date CHECK ((base_date IN ('CREDIT', 'PAYMENT'))),
    CONSTRAINT ck_withholding_tax_rules_rate CHECK (((rate >= (0)) AND (rate < (1)))),
    CONSTRAINT ck_withholding_tax_rules_type CHECK ((dividend_type IN ('DIVIDENDO', 'JCP', 'RENDIMENTO', 'AMORTIZACAO', 'RESTITUICAO_CAPITAL'))),
    CONSTRAINT ck_withholding_tax_rules_validity CHECK (((valid_from IS NULL) OR (valid_to IS NULL) OR (valid_from <= valid_to)))
);

CREATE INDEX idx_withholding_tax_rules_type ON withholding_tax_rules (dividend_type, asset_type_id);


-- -----------------------------------------------------------------------------
-- integration_connections
-- -----------------------------------------------------------------------------
-- Origem: V25__integracoes, V40__integracao_b3
--
-- [V25] Conexao: uma por conta na fonte. Um usuario pode ter varias (duas contas da
-- Binance, por exemplo), diferenciadas pelo apelido.
-- provider: so BINANCE por enquanto. Fonte nova (outra corretora, arquivo,
-- Open Finance) entra por migration, junto com o conector dela.
-- status:
--   ACTIVE                credencial valida e so de leitura
--   INVALID_CREDENTIALS   a fonte recusou a chave (apagada, expirada, errada)
--   PERMISSION_TOO_BROAD  a chave negocia, saca ou transfere: sync bloqueado
--   REVOKED               o usuario desconectou; a credencial ja foi apagada
-- consent_accepted_at: quando o usuario aceitou o texto de consentimento
-- (o que e lido, para que, por quanto tempo). LGPD, 6.4 do plano.
--
-- [V40] Integracao com a B3 (docs/planos/PLANO_INTEGRACAO_B3.md, F2 — G7 e G8).
-- So duas regras mudam; nenhuma tabela e nenhuma coluna nova (principio P2):
--   provider: a B3 entra como fonte, ao lado da Binance;
--   status:   PENDING_AUTHORIZATION — o usuario informou o CPF, mas ainda nao autorizou o
--             Activest na Area do Investidor da B3 (opt-in). A credencial (o CPF, cifrado)
--             fica em integration_credentials, como a chave da Binance.
CREATE TABLE integration_connections (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    provider            VARCHAR(30) NOT NULL,
    label               VARCHAR(100) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    consent_accepted_at TIMESTAMP NOT NULL,
    last_sync_at        TIMESTAMP,
    last_error          TEXT,
    user_id             UUID NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT integration_connections_pkey PRIMARY KEY (id),
    CONSTRAINT uk_integration_connections_label UNIQUE (user_id, label),
    CONSTRAINT fk_integration_connections_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_integration_connections_provider CHECK ((provider IN ('BINANCE', 'B3'))),
    CONSTRAINT ck_integration_connections_status CHECK ((status IN ('ACTIVE', 'PENDING_AUTHORIZATION', 'INVALID_CREDENTIALS', 'PERMISSION_TOO_BROAD', 'REVOKED')))
);

CREATE INDEX idx_integration_connections_user ON integration_connections (user_id);


-- -----------------------------------------------------------------------------
-- integration_credentials
-- -----------------------------------------------------------------------------
-- Origem: V25__integracoes
--
-- [V25] Credencial: 1:1 com a conexao, e a unica tabela com segredo.
-- ciphertext  JSON da credencial ({"apiKey": ..., "apiSecret": ...}) cifrado
--             com AES-256-GCM; inclui a tag de autenticacao de 16 bytes.
-- iv          12 bytes, novo a cada cifragem. Nunca se repete para a mesma chave.
-- key_version qual chave-mestra cifrou: permite rotacionar sem perder o que ja
--             esta gravado (a antiga continua configurada so para ler).
-- fingerprint os ultimos 4 caracteres da chave de API, o unico pedaco que a tela
--             mostra ("****a7K2").
-- permissions JSON da ultima checagem de permissao na fonte (6.4).
-- A cifragem usa o id da conexao como dado associado (AAD): um ciphertext
-- copiado para outra linha nao decifra.
-- Revogar a conexao APAGA esta linha (nao ha soft delete de segredo).
CREATE TABLE integration_credentials (
    connection_id UUID NOT NULL,
    ciphertext    BYTEA NOT NULL,
    iv            BYTEA NOT NULL,
    key_version   SMALLINT NOT NULL,
    fingerprint   VARCHAR(8) NOT NULL,
    permissions   JSONB,
    validated_at  TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT integration_credentials_pkey PRIMARY KEY (connection_id),
    CONSTRAINT fk_integration_credentials_connection FOREIGN KEY (connection_id) REFERENCES integration_connections(id) ON DELETE CASCADE,
    CONSTRAINT ck_integration_credentials_iv CHECK ((octet_length(iv) = 12)),
    CONSTRAINT ck_integration_credentials_key_version CHECK ((key_version > 0))
);


-- -----------------------------------------------------------------------------
-- integration_cursors
-- -----------------------------------------------------------------------------
-- Origem: V25__integracoes
--
-- [V25] Cursor: onde cada fluxo da fonte parou, para a sincronizacao ser incremental
-- (a primeira busca o historico inteiro; as seguintes, so o que e novo).
-- stream e o fluxo na linguagem do conector: 'spot:BTCBRL', 'convert',
-- 'fiat_payments'. cursor_value e opaco para o banco (ultimo id ou instante).
CREATE TABLE integration_cursors (
    connection_id UUID NOT NULL,
    stream        VARCHAR(100) NOT NULL,
    cursor_value  VARCHAR(200) NOT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_integration_cursors PRIMARY KEY (connection_id, stream),
    CONSTRAINT fk_integration_cursors_connection FOREIGN KEY (connection_id) REFERENCES integration_connections(id) ON DELETE CASCADE
);


-- -----------------------------------------------------------------------------
-- integration_runs
-- -----------------------------------------------------------------------------
-- Origem: V25__integracoes, V26__integracoes_confirmacao, V27__integracoes_fila
--
-- [V25] Execucao: cada sincronizacao, com o resultado que a tela mostra.
-- trigger_type  MANUAL (botao), SCHEDULED (rotina diaria), CONNECTION (a
--               primeira, disparada ao conectar)
-- status        QUEUED -> RUNNING -> AWAITING_CONFIRMATION (pre-visualizacao,
--               6.6/6.7) -> SUCCEEDED; ou FAILED / CANCELLED
-- contagens     importadas, ja existentes, nao mapeadas, possiveis duplicatas
--
-- [V26] next_cursors    onde cada fluxo vai parar se a execucao for confirmada. So
--                 vira integration_cursors na confirmacao: avancar antes faria
--                 uma operacao vista e nao confirmada nunca mais voltar.
-- fiat_deposited  soma dos depositos em reais do lote (conferencia, 4.6).
--
-- [V27] Sincronizacao em segundo plano (PLANO_CRIPTO_BINANCE.md, F5).
-- A fila e a propria integration_runs: POST .../preview cria uma execucao
-- QUEUED e responde na hora; o IntegrationWorker pega a mais antiga com
-- FOR UPDATE SKIP LOCKED (uma por conexao por vez), busca na corretora e a
-- deixa AWAITING_CONFIRMATION — ou confirma sozinho, na rotina diaria, quando
-- nao ha nada para o usuario decidir.
-- balances  saldo da corretora no momento da busca (ativo -> quantidade),
--           para o aviso de divergencia da regra E3: posicao do Activest x
--           saldo da corretora, por ativo.
CREATE TABLE integration_runs (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    trigger_type    VARCHAR(20) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    imported_count  INTEGER NOT NULL DEFAULT 0,
    existing_count  INTEGER NOT NULL DEFAULT 0,
    unmapped_count  INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    error           TEXT,
    next_cursors    JSONB,
    fiat_deposited  NUMERIC(18,2),
    balances        JSONB,
    connection_id   UUID NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT integration_runs_pkey PRIMARY KEY (id),
    CONSTRAINT fk_integration_runs_connection FOREIGN KEY (connection_id) REFERENCES integration_connections(id) ON DELETE CASCADE,
    CONSTRAINT ck_integration_runs_counts CHECK (((imported_count >= 0) AND (existing_count >= 0) AND (unmapped_count >= 0) AND (duplicate_count >= 0))),
    CONSTRAINT ck_integration_runs_status CHECK ((status IN ('QUEUED', 'RUNNING', 'AWAITING_CONFIRMATION', 'SUCCEEDED', 'FAILED', 'CANCELLED'))),
    CONSTRAINT ck_integration_runs_trigger CHECK ((trigger_type IN ('MANUAL', 'SCHEDULED', 'CONNECTION')))
);

CREATE INDEX idx_integration_runs_connection ON integration_runs (connection_id, created_at DESC);
CREATE INDEX idx_integration_runs_status ON integration_runs (status, created_at);


-- -----------------------------------------------------------------------------
-- issuers
-- -----------------------------------------------------------------------------
-- Origem: V28__issuers, V30__seed_issuers
--
-- [V28] Emissores: companhias abertas e FIIs (PLANO_EMPRESAS_BALANCOS.md, F1, migration M1).
-- Catalogo global, como assets: o balanco da Petrobras e o mesmo para todo
-- usuario. Um emissor por CNPJ; o ativo aponta para ele (assets.issuer_id).
-- As colunas de COMPANY e de FII convivem na mesma tabela, nulas no outro tipo:
-- sao poucas, e a tabela unica deixa a ligacao ativo -> emissor com uma FK so.
-- De onde vem cada coluna:
--   COMPANY  cadastro de companhias abertas da CVM (cad_cia_aberta.csv), FCA
--            (geral e valor_mobiliario) e a classificacao setorial da B3
--            (GetDetail: setor / subsetor / segmento).
--   FII      informe mensal da CVM (inf_mensal_fii_geral). O segmento curado
--            (segment) vem do seed de emissores (M3), nunca da importacao: o
--            Segmento_Atuacao da CVM e fraco ("Multicategoria" em metade dos
--            fundos) e fica so como informacao (cvm_segment).
CREATE TABLE issuers (
    id                    BIGSERIAL,
    kind                  VARCHAR(10) NOT NULL,
    cnpj                  VARCHAR(14) NOT NULL,
    cvm_code              INTEGER,
    legal_name            VARCHAR(255) NOT NULL,
    trade_name            VARCHAR(255),
    registration_status   VARCHAR(20),
    website               VARCHAR(255),
    cvm_sector            VARCHAR(120),
    b3_sector             VARCHAR(120),
    b3_subsector          VARCHAR(120),
    b3_segment            VARCHAR(120),
    registration_category VARCHAR(20),
    listing_segment       VARCHAR(80),
    fiscal_year_end_month SMALLINT,
    statement_layout      VARCHAR(20),
    segment               VARCHAR(120),
    cvm_segment           VARCHAR(120),
    administrator_name    VARCHAR(255),
    administrator_cnpj    VARCHAR(14),
    management_type       VARCHAR(20),
    source_updated_at     TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT issuers_pkey PRIMARY KEY (id),
    CONSTRAINT uk_issuers_cnpj UNIQUE (cnpj),
    CONSTRAINT uk_issuers_cvm_code UNIQUE (cvm_code),
    CONSTRAINT ck_issuers_cnpj CHECK ((cnpj ~ '^[0-9]{14}$')),
    CONSTRAINT ck_issuers_cvm_code CHECK (((kind = 'COMPANY') OR (cvm_code IS NULL))),
    CONSTRAINT ck_issuers_fiscal_month CHECK (((fiscal_year_end_month >= 1) AND (fiscal_year_end_month <= 12))),
    CONSTRAINT ck_issuers_kind CHECK ((kind IN ('COMPANY', 'FII'))),
    CONSTRAINT ck_issuers_layout CHECK ((statement_layout IN ('COMERCIAL', 'BANCO', 'SEGURADORA')))
);

CREATE INDEX idx_issuers_kind ON issuers (kind);


-- -----------------------------------------------------------------------------
-- assets
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V4__seed_assets_acao, V5__seed_assets_fii, V6__seed_assets_tesouro_direto, V7__seed_assets_renda_fixa, V13__asset_names_brapi, V14__deactivate_delisted_assets, V15__new_assets_brapi, V17__seed_assets_etf, V18__seed_assets_bdr, V19__seed_assets_cripto, V20__proventos_b3, V21__asset_logos, V28__issuers, V30__seed_issuers, V31__asset_categories_without_segment, V37__assets_sem_estado, V54__emissoras_renda_fixa
--
-- [V1] Catálogo de ativos: o que o instrumento É.
-- Nada aqui descreve uma compra. Vencimento e remuneração nunca moram nesta
-- tabela: os que são públicos e do papel (Tesouro Direto) ficam em
-- asset_terms, e os de cada aplicação (CDB, LCI, debênture) em
-- fixed_income_notes, que pertence ao usuário.
-- O catálogo não tem user_id de propósito: um ativo é o mesmo para todos.
--
-- [V19] Criptomoedas — 227 ativos e 15 categorias (PLANO_BRAPI.md, B11).
-- Fonte: CoinGecko, as 250 maiores por valor de mercado em 24/09/2026, menos 23
-- que nao sao cripto de varejo (fundos e titulos tokenizados, stablecoins com
-- rendimento, acao tokenizada e simbolos em ideogramas) — planilha
-- docs/brapi/Criptos_CoinGecko_2026-09-24.xlsx. Sem cotacao: o preco entra pela
-- rotina de fechamento das 19:00, e so para o que estiver em carteira.
-- price_source_ref e o id do ativo no provedor de cotacao ('bitcoin',
-- 'ethereum'). O ticker (BTC) e o que a pessoa le; o id e o que a API de preco
-- entende. Antes a traducao era um mapa fixo de 14 moedas dentro do
-- CoinGeckoClient, com um id vencido (MATIC -> matic-network). Coluna generica
-- de proposito: serve a qualquer provedor que identifique ativo por outro
-- codigo.
-- Categoria, mesma regra dos ETFs e BDRs: description = tema; name = tema em
-- maiusculas sem acento; segment = categoria da CoinGecko que definiu o tema,
-- traduzida aqui (Layer 1 (L1) -> Camada 1 (L1)), ou Indefinido.
--
-- [V20] assets: identificadores que as fontes usam
-- isin    a B3 devolve proventos por ISIN, nao por ticker (PETR3 e PETR4 vem na
--         mesma resposta do emissor PETR). Preenchida pela rotina.
-- cnpj    so FII: o FNET (historico de proventos alem de 12 meses) busca por
--         CNPJ do fundo. Preenchida pela rotina a partir do cadastro da CVM.
-- cash_history_loaded_at  quando o historico longo do ativo foi carregado. Nulo
--         = nunca; a rotina carrega uma vez, quando alguma posicao comeca antes
--         do evento mais antigo guardado.
--
-- [V21] Logos dos ativos (PLANO_BRAPI.md, F7, B17, B18 e B25).
-- O arquivo NAO mora no banco: fica na pasta data/logos/ da raiz do projeto,
-- montada no container da API. Aqui so o nome do arquivo guardado.
-- logo_path        nome do arquivo em data/logos ('PETR4.svg'). Nulo = sem logo;
--                  a tela mostra as iniciais do ticker.
-- logo_checked_at  quando a rotina procurou o logo pela ultima vez, achando ou
--                  nao. Nulo = nunca procurado: e assim que a rotina reconhece
--                  ativo novo na subida da API, sem varrer o catalogo inteiro.
--
-- [V28] Ligacao ativo -> emissor (F3). Nula ate a ligacao rodar, e para os tipos que
-- nao tem emissor (BDR, ETF, Tesouro, renda fixa, cripto).
-- share_class        ON, PN, PNA..PND ou UNIT (so ACAO). O fracionario herda
--                    a classe do ticker sem o F.
-- issuer_link_source como a ligacao foi feita: FCA, PREFIXO (raiz do ticker),
--                    B3, ISIN ou MANUAL. MANUAL nunca e sobrescrito pela rotina.
--
-- [V37] M7 (PLANO_EMPRESAS_BALANCOS.md, 2.3 e F10): sai de assets o que nao e do ativo.
--   cnpj                   e do emissor: issuers.cnpj (em 28/09/2026 os 6 preenchidos eram
--                          iguais ao do emissor ligado).
--   cash_history_loaded_at estado da rotina de proventos: o historico da B3 fica em
--                          data/b3/proventos/{EMISSOR}.json e o ativo com evento B3_HISTORICO
--                          ja tem o dele; o do FII e o que esta em data/fnet e ja foi gravado.
--   logo_path              o arquivo em data/logos/{TICKER}.{ext} e o estado (AssetLogos).
--   logo_checked_at        idem; a rotina mensal procura so quem nao tem arquivo.
CREATE TABLE assets (
    id                 BIGSERIAL,
    ticker             VARCHAR(255) NOT NULL,
    name               VARCHAR(255) NOT NULL,
    current_price      NUMERIC(18,8),
    price_updated_at   TIMESTAMP,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    price_source_ref   VARCHAR(100),
    isin               VARCHAR(12),
    share_class        VARCHAR(10),
    issuer_link_source VARCHAR(10),
    asset_type_id      BIGINT NOT NULL,
    asset_category_id  BIGINT,
    issuer_id          BIGINT,
    CONSTRAINT assets_pkey PRIMARY KEY (id),
    CONSTRAINT uk_assets_ticker UNIQUE (ticker),
    CONSTRAINT fk_assets_category FOREIGN KEY (asset_category_id) REFERENCES asset_categories(id),
    CONSTRAINT fk_assets_issuer FOREIGN KEY (issuer_id) REFERENCES issuers(id),
    CONSTRAINT fk_assets_type FOREIGN KEY (asset_type_id) REFERENCES asset_types(id),
    CONSTRAINT ck_assets_issuer_link_source CHECK ((issuer_link_source IN ('FCA', 'PREFIXO', 'B3', 'ISIN', 'MANUAL'))),
    CONSTRAINT ck_assets_share_class CHECK ((share_class IN ('ON', 'PN', 'PNA', 'PNB', 'PNC', 'PND', 'UNIT')))
);

CREATE INDEX idx_assets_category ON assets (asset_category_id);
CREATE INDEX idx_assets_issuer ON assets (issuer_id);
CREATE INDEX idx_assets_ticker ON assets (ticker);
CREATE INDEX idx_assets_type ON assets (asset_type_id);


-- -----------------------------------------------------------------------------
-- asset_terms
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V8__seed_asset_terms_tesouro
--
-- [V1] Parâmetros públicos de um título, 1:1 com assets.
-- Hoje só o Tesouro Direto preenche isto. "Tesouro IPCA+ 2029" vence em
-- 15/05/2029 e remunera IPCA mais spread para qualquer investidor: é
-- característica do papel, e por isso fica no catálogo. Um CDB não tem nada
-- disso — cada aplicação negocia o seu, e essa informação mora em
-- fixed_income_notes.
-- Tabela separada, e não colunas em assets, porque 130 dos 1264 ativos têm
-- parâmetros e os outros 1134 carregariam quatro colunas nulas.
-- Sem id próprio: a chave é o ativo. Um BIGSERIAL aqui só abriria espaço para
-- duas linhas de parâmetros para o mesmo papel. E ativo sem parâmetros não
-- ganha linha vazia: a ausência da linha JÁ é a informação "não tem prazo nem
-- indexador".
-- A taxa NÃO está aqui de propósito. No Tesouro ela é do dia da compra, não do
-- título: dois investidores levam o mesmo papel a IPCA + 6,2% e IPCA + 5,8%
-- conforme quando compraram. Taxa é da nota.
CREATE TABLE asset_terms (
    asset_id      BIGINT NOT NULL,
    maturity_date DATE,
    rate_type     VARCHAR(20),
    indexer       VARCHAR(20),
    CONSTRAINT asset_terms_pkey PRIMARY KEY (asset_id),
    CONSTRAINT fk_asset_terms_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE,
    CONSTRAINT ck_asset_terms_indexer CHECK ((indexer IN ('CDI', 'SELIC', 'IPCA', 'IGPM', 'INPC', 'TR'))),
    CONSTRAINT ck_asset_terms_rate_coherence CHECK ((((rate_type IS NULL) AND (indexer IS NULL)) OR ((rate_type = 'PREFIXADO') AND (indexer IS NULL)) OR ((rate_type IN ('POS_FIXADO', 'HIBRIDO')) AND (indexer IS NOT NULL)))),
    CONSTRAINT ck_asset_terms_rate_type CHECK ((rate_type IN ('PREFIXADO', 'POS_FIXADO', 'HIBRIDO')))
);

CREATE INDEX idx_asset_terms_maturity ON asset_terms (maturity_date);


-- -----------------------------------------------------------------------------
-- asset_price_history
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V17__seed_assets_etf, V18__seed_assets_bdr, V29__price_history_source, V44__tesouro_preco_oficial, V58__price_history_binance
--
-- [V1] Histórico diário de cotação por ativo, gravado no fechamento do pregão.
-- Hoje a tabela só escreve: a leitura nunca foi ligada (ver PENDENCIAS.txt,
-- seção 11).
--
-- [V29] Origem de cada pregao em asset_price_history (PLANO_EMPRESAS_BALANCOS.md, F2, migration M2).
-- A serie historica da B3 (COTAHIST) passa a carregar o historico diario desde
-- 2010; a BRAPI continua gravando o fechamento do dia e a CoinGecko o de cripto.
-- As fontes convivem na mesma tabela (conferido em 27/09/2026: fechamento da
-- BRAPI identico ao da B3). Regra: dia gravado pela B3 nao e sobrescrito pela
-- BRAPI — a serie oficial vence.
-- Linhas existentes: BRAPI (seeds V17/V18 e fechamento), exceto as de cripto,
-- que vieram da CoinGecko.
CREATE TABLE asset_price_history (
    id             BIGSERIAL,
    reference_date DATE NOT NULL,
    open_price     NUMERIC(18,8),
    close_price    NUMERIC(18,8),
    high_price     NUMERIC(18,8),
    low_price      NUMERIC(18,8),
    volume         BIGINT,
    source         VARCHAR(15) NOT NULL DEFAULT 'BRAPI',
    asset_id       BIGINT NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT asset_price_history_pkey PRIMARY KEY (id),
    CONSTRAINT uk_price_history_day UNIQUE (asset_id, reference_date),
    CONSTRAINT fk_price_history_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT ck_price_history_source CHECK ((source IN ('BRAPI', 'COINGECKO', 'B3_COTAHIST', 'TESOURO', 'BINANCE')))
);

CREATE INDEX idx_price_history_asset_date ON asset_price_history (asset_id, reference_date);


-- -----------------------------------------------------------------------------
-- ceiling_prices
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V46__preco_teto_modelo_v2, V49__preco_teto_alerta
--
-- [V1] Preço teto definido pelo usuário para um ativo.
--
-- [V49] Alerta de preço do Preço Teto (docs/planos/PLANO_NOTIFICACOES.md, 3.2 e 5.5): "avise quando o preço atual ficar
-- abaixo do Preço Ideal". Colunas novas em tabela existente nascem com o default (REGRAS 8.1).
--   alert_enabled     — o interruptor da coluna "Alerta" da lista (desligado por padrão);
--   alert_below_ideal — o preço estava abaixo do ideal na última conferência: só avisa de novo depois de voltar
--   acima;
--   alert_last_on     — último dia avisado: no máximo um aviso por ativo por dia.
CREATE TABLE ceiling_prices (
    id                   UUID NOT NULL DEFAULT gen_random_uuid(),
    shares               BIGINT NOT NULL,
    roe_pct              NUMERIC(9,4) NOT NULL,
    payout_pct           NUMERIC(9,4) NOT NULL,
    discount_rate_pct    NUMERIC(9,4) NOT NULL,
    perpetual_growth_pct NUMERIC(9,4) NOT NULL,
    safety_margin_pct    NUMERIC(9,4) NOT NULL,
    price                NUMERIC(18,8) NOT NULL,
    inputs_origin        JSONB NOT NULL,
    notes                VARCHAR(500),
    calculated_at        TIMESTAMP NOT NULL,
    alert_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    alert_below_ideal    BOOLEAN NOT NULL DEFAULT FALSE,
    alert_last_on        DATE,
    user_id              UUID NOT NULL,
    asset_id             BIGINT NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ceiling_prices_pkey PRIMARY KEY (id),
    CONSTRAINT uk_ceiling_prices_scope UNIQUE (user_id, asset_id),
    CONSTRAINT fk_ceiling_prices_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT fk_ceiling_prices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_ceiling_prices_margin CHECK (((safety_margin_pct >= (0)) AND (safety_margin_pct < (100)))),
    CONSTRAINT ck_ceiling_prices_price CHECK ((price > (0))),
    CONSTRAINT ck_ceiling_prices_shares CHECK ((shares > 0))
);

CREATE INDEX idx_ceiling_prices_user ON ceiling_prices (user_id);


-- -----------------------------------------------------------------------------
-- asset_cash_events
-- -----------------------------------------------------------------------------
-- Origem: V20__proventos_b3, V22__cash_event_installments, V24__cash_event_extrato_b3, V35__amortizacao_rf, V38__catalogo_sem_extrato, V39__data_pagamento_levantamento, V60__limpeza_proventos_indevidos
--
-- [V20] asset_cash_events — provento em dinheiro, por papel
-- label e o rotulo como a fonte escreve ('JRS CAP PROPRIO'); type e o nosso
-- (JCP). type nulo = rotulo que ainda nao sabemos mapear: o evento fica gravado,
-- a rotina loga e nenhum provento e gerado ate o mapeamento existir.
-- value_per_share com 11 casas: e a precisao da B3 ('0,47156696000'), e o
-- historico longo divide por lote (1000 acoes nos anos 90).
-- payment_date e nula quando a fonte nao traz (o historico longo da B3 nao tem
-- data de pagamento).
-- A unicidade e o que impede a mesma noticia, vinda de duas fontes (B3 recente,
-- B3 historico, FNET), de virar dois proventos.
--
-- [V22] Parcelas do mesmo provento (PLANO_BRAPI.md, F5).
-- A B3 publica o provento pago em parcelas como linhas iguais: mesmo tipo,
-- mesma data-com, mesmo valor por acao, so a data de pagamento muda. PETR4,
-- data-com 01/06/2026: JCP de R$ 0,35048636 pago em 20/08 E outro de R$
-- 0,35048636 pago em 21/09. A unicidade da V20 (ativo, tipo, data-com, valor)
-- tratava a segunda parcela como a mesma noticia repetida e ela sumia — o
-- acionista recebia metade.
-- seq numera as linhas iguais dentro da mesma resposta da fonte (1, 2...). A
-- mesma parcela vinda de outra fonte (o historico longo da B3 tambem lista as
-- duas) casa pelo mesmo numero, e a deduplicacao entre fontes continua valendo.
--
-- [V24] Proventos lancados a partir do extrato da Area do Investidor da B3.
-- Enquanto nao houver fonte fixa para o historico de FII alem de 12 meses (o
-- FNET e instavel), as lacunas do catalogo sao preenchidas pelo extrato de
-- proventos que o investidor baixa da B3: POST /api/v1/admin/proventos/extrato.
-- source = 'EXTRATO_B3'   evento criado a partir do extrato. O extrato traz o
--                         pagamento, nao a data-com: a data-com e DEDUZIDA do
--                         padrao do fundo (ex.: ultimo pregao do mes anterior
--                         ao pagamento) e conferida contra as cotas do extrato.
-- payment_date_source     de onde veio a data de pagamento, quando NAO veio da
--                         propria fonte do evento:
--   NULL        veio da fonte do evento (B3, FNET) — oficial
--   EXTRATO_B3  veio do extrato do investidor
--   DEDUZIDA    deduzida do padrao de pagamento da empresa (ex.: a 2a parcela
--               de JCP da Cemig em 30/12), ainda sem confirmacao
-- Quando uma fonte oficial traz a data, ela substitui a do extrato ou a
-- deduzida e a coluna volta a NULL (ProventosSyncService.saveCashEvent).
--
-- [V39] Datas de pagamento levantadas (PLANO_CARGA_INICIAL.md, secao 11 - D14).
-- O historico longo da B3 nao traz a data de pagamento dos proventos de acao; os relatorios da CVM
-- (payment_date_source = 'CVM') so existem de 2022 em diante. O levantamento manual de 2010-2026
-- (data/levantamento/datas-pagamento.json, aplicado pela adm-api) completa o resto com
-- payment_date_source = 'LEVANTAMENTO'. Como a CVM, cede a data da propria fonte do evento
-- (CashEventWriter). Sem dados a migrar: so a regra.
CREATE TABLE asset_cash_events (
    id                  BIGSERIAL,
    type                VARCHAR(30),
    label               VARCHAR(60) NOT NULL,
    data_com            DATE NOT NULL,
    payment_date        DATE,
    value_per_share     NUMERIC(24,11) NOT NULL,
    approved_on         DATE,
    related_to          VARCHAR(100),
    isin                VARCHAR(12),
    income_tax_exempt   BOOLEAN,
    source              VARCHAR(20) NOT NULL,
    source_ref          VARCHAR(100),
    fetched_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    seq                 SMALLINT NOT NULL DEFAULT 1,
    payment_date_source VARCHAR(20),
    asset_id            BIGINT NOT NULL,
    CONSTRAINT asset_cash_events_pkey PRIMARY KEY (id),
    CONSTRAINT uk_asset_cash_events UNIQUE NULLS NOT DISTINCT (asset_id, type, data_com, value_per_share, seq),
    CONSTRAINT fk_asset_cash_events_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT ck_asset_cash_events_payment_date_source CHECK (((payment_date_source IS NULL) OR ((payment_date_source IN ('CVM', 'LEVANTAMENTO')) AND (payment_date IS NOT NULL)))),
    CONSTRAINT ck_asset_cash_events_source CHECK ((source IN ('B3', 'B3_HISTORICO', 'FNET'))),
    CONSTRAINT ck_asset_cash_events_type CHECK ((type IN ('DIVIDENDO', 'JCP', 'RENDIMENTO', 'AMORTIZACAO', 'RESTITUICAO_CAPITAL')))
);

CREATE INDEX idx_asset_cash_events_asset_date ON asset_cash_events (asset_id, data_com);


-- -----------------------------------------------------------------------------
-- asset_stock_events
-- -----------------------------------------------------------------------------
-- Origem: V20__proventos_b3
--
-- [V20] asset_stock_events — desdobramento, grupamento e bonificacao, por papel
-- factor como a B3 publica (conferido em casos reais, A12):
--   DESDOBRAMENTO  percentual de acoes novas: 100 = 1 vira 2 (PETR 2008),
--                  300 = 1 vira 4 (MGLU 2020), 900 = 1 vira 10 (MXRF 2017)
--   GRUPAMENTO     multiplicador: 0,1 = 10 viram 1 (MGLU 2024)
--   BONIFICACAO    percentual de acoes novas: 5 = 5 novas a cada 100 (MGLU 2025)
-- assigned_cost e o custo atribuido por acao bonificada. A B3 nao informa
-- (remarks vazio nos casos conferidos): nulo = custo zero provisorio (B27).
CREATE TABLE asset_stock_events (
    id            BIGSERIAL,
    type          VARCHAR(20) NOT NULL,
    data_com      DATE NOT NULL,
    factor        NUMERIC(24,11) NOT NULL,
    assigned_cost NUMERIC(18,8),
    approved_on   DATE,
    isin          VARCHAR(12),
    source        VARCHAR(20) NOT NULL,
    fetched_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    asset_id      BIGINT NOT NULL,
    CONSTRAINT asset_stock_events_pkey PRIMARY KEY (id),
    CONSTRAINT uk_asset_stock_events UNIQUE (asset_id, type, data_com, factor),
    CONSTRAINT fk_asset_stock_events_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT ck_asset_stock_events_factor CHECK ((factor > (0))),
    CONSTRAINT ck_asset_stock_events_type CHECK ((type IN ('DESDOBRAMENTO', 'GRUPAMENTO', 'BONIFICACAO')))
);

CREATE INDEX idx_asset_stock_events_asset_date ON asset_stock_events (asset_id, data_com);


-- -----------------------------------------------------------------------------
-- integration_crypto_exclusions
-- -----------------------------------------------------------------------------
-- Origem: V25__integracoes
--
-- [V25] Exclusoes de cripto (D9, regra E2): operacao importada que o usuario
-- excluiu. A sincronizacao seguinte a encontra na corretora, consulta esta
-- tabela e nao recria. "Restaurar" (E4) apaga a linha e a proxima
-- sincronizacao traz a operacao de volta.
-- Uma tabela por dominio: a renda fixa tera integration_fixed_income_exclusions
-- se o Open Finance vier a precisar.
-- source_snapshot: a operacao como estava ao ser excluida, para listar o que
-- foi excluido e para restaurar.
CREATE TABLE integration_crypto_exclusions (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    external_id     VARCHAR(200) NOT NULL,
    source_snapshot JSONB NOT NULL,
    excluded_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    connection_id   UUID NOT NULL,
    asset_id        BIGINT NOT NULL,
    CONSTRAINT integration_crypto_exclusions_pkey PRIMARY KEY (id),
    CONSTRAINT uk_integration_crypto_exclusions UNIQUE (connection_id, external_id),
    CONSTRAINT fk_integration_crypto_exclusions_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT fk_integration_crypto_exclusions_connection FOREIGN KEY (connection_id) REFERENCES integration_connections(id) ON DELETE CASCADE
);

CREATE INDEX idx_integration_crypto_exclusions_asset ON integration_crypto_exclusions (asset_id);


-- -----------------------------------------------------------------------------
-- unit_compositions
-- -----------------------------------------------------------------------------
-- Origem: V28__issuers, V30__seed_issuers
--
-- [V28] Composicao das units: KLBN11 = 1 ON + 4 PN. Vem do FCA (texto livre, lido por
-- regex na F3) ou da ligacao manual. LPA e VPA da unit = do emissor x soma.
CREATE TABLE unit_compositions (
    asset_id    BIGINT NOT NULL,
    share_class VARCHAR(10) NOT NULL,
    quantity    INTEGER NOT NULL,
    CONSTRAINT pk_unit_compositions PRIMARY KEY (asset_id, share_class),
    CONSTRAINT fk_unit_compositions_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE,
    CONSTRAINT ck_unit_compositions_class CHECK ((share_class IN ('ON', 'PN', 'PNA', 'PNB', 'PNC', 'PND'))),
    CONSTRAINT ck_unit_compositions_quantity CHECK ((quantity > 0))
);


-- -----------------------------------------------------------------------------
-- cvm_documents
-- -----------------------------------------------------------------------------
-- Origem: V32__cvm_statements
CREATE TABLE cvm_documents (
    id             BIGSERIAL,
    doc_type       VARCHAR(3) NOT NULL,
    reference_date DATE NOT NULL,
    version        SMALLINT NOT NULL,
    cvm_doc_id     INTEGER,
    received_at    DATE,
    consolidated   BOOLEAN NOT NULL,
    issuer_id      BIGINT NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT cvm_documents_pkey PRIMARY KEY (id),
    CONSTRAINT uk_cvm_documents UNIQUE (issuer_id, doc_type, reference_date),
    CONSTRAINT fk_cvm_documents_issuer FOREIGN KEY (issuer_id) REFERENCES issuers(id),
    CONSTRAINT ck_cvm_documents_type CHECK ((doc_type IN ('DFP', 'ITR')))
);

CREATE INDEX ix_cvm_documents_reference ON cvm_documents (issuer_id, reference_date);


-- -----------------------------------------------------------------------------
-- cvm_statement_values
-- -----------------------------------------------------------------------------
-- Origem: V32__cvm_statements
--
-- [V32] Uma metrica de um documento num periodo. Balanco: period_start nulo (posicao na data). DRE do ITR:
-- o trimestre e o acumulado do exercicio, dois periodos. Valores em reais; LPA em reais por acao.
CREATE TABLE cvm_statement_values (
    metric       VARCHAR(40) NOT NULL,
    period_start DATE,
    period_end   DATE NOT NULL,
    value        NUMERIC(24,6) NOT NULL,
    document_id  BIGINT NOT NULL,
    CONSTRAINT uk_cvm_statement_values UNIQUE NULLS NOT DISTINCT (document_id, metric, period_start, period_end),
    CONSTRAINT fk_cvm_statement_values_document FOREIGN KEY (document_id) REFERENCES cvm_documents(id) ON DELETE CASCADE
);


-- -----------------------------------------------------------------------------
-- share_counts
-- -----------------------------------------------------------------------------
-- Origem: V32__cvm_statements, V36__share_scale_evento
--
-- [V32] Quantidade de acoes na data do documento. Como veio da fonte; em unidades = quantidade * scale_factor.
--   source COMPOSICAO: DFP/ITR (2020+), escala desconhecida, conferida pelo FRE ou pelo LPA
--                      (ShareScale); tesouraria informada
--   source FRE:        capital integralizado do FRE na data (antes de 2020), em unidades; sem tesouraria
-- scale_check VIZINHO: o que sobrou sem conferencia e decidido pelo documento validado mais proximo no
--                      tempo, anterior ou seguinte (companhia recem-listada, sem FRE nem LPA ainda).
-- scale_status REVISAR: nenhuma conferencia fechou; o fator fica nulo e o indicador nao usa a linha.
--
-- [V36] scale_check EVENTO: a quantidade que nao fechou pelo FRE, pelo LPA nem pelo vizinho e
-- conferida contra a do balanco validado anterior ajustada pelos grupamentos, desdobramentos
-- e bonificacoes registrados entre as duas datas (FinancialStatementImportService.resolveByStockEvents).
CREATE TABLE share_counts (
    document_id        BIGINT NOT NULL,
    source             VARCHAR(12) NOT NULL,
    ordinary_shares    BIGINT,
    preferred_shares   BIGINT,
    total_shares       BIGINT NOT NULL,
    ordinary_treasury  BIGINT,
    preferred_treasury BIGINT,
    total_treasury     BIGINT,
    scale_factor       INTEGER,
    scale_status       VARCHAR(10) NOT NULL,
    scale_check        VARCHAR(10),
    CONSTRAINT share_counts_pkey PRIMARY KEY (document_id),
    CONSTRAINT fk_share_counts_document FOREIGN KEY (document_id) REFERENCES cvm_documents(id) ON DELETE CASCADE,
    CONSTRAINT ck_share_counts_check CHECK (((scale_check IS NULL) OR (scale_check IN ('FRE', 'LPA', 'VIZINHO', 'EVENTO')))),
    CONSTRAINT ck_share_counts_factor CHECK (((scale_factor IS NOT NULL) OR (scale_status = 'REVISAR'))),
    CONSTRAINT ck_share_counts_source CHECK ((source IN ('COMPOSICAO', 'FRE'))),
    CONSTRAINT ck_share_counts_status CHECK ((scale_status IN ('OK', 'REVISAR', 'MANUAL')))
);


-- -----------------------------------------------------------------------------
-- company_quarters
-- -----------------------------------------------------------------------------
-- Origem: V33__company_quarters, V45__dividendos_pagos
CREATE TABLE company_quarters (
    issuer_id              BIGINT NOT NULL,
    period_end             DATE NOT NULL,
    fiscal_year_start      DATE,
    revenue_q              NUMERIC(24,2),
    ebit_q                 NUMERIC(24,2),
    net_income_q           NUMERIC(24,2),
    net_income_parent_q    NUMERIC(24,2),
    fco_q                  NUMERIC(24,2),
    dividends_declared_q   NUMERIC(24,2),
    revenue_ttm            NUMERIC(24,2),
    ebit_ttm               NUMERIC(24,2),
    net_income_ttm         NUMERIC(24,2),
    net_income_parent_ttm  NUMERIC(24,2),
    fco_ttm                NUMERIC(24,2),
    dividends_declared_ttm NUMERIC(24,2),
    equity                 NUMERIC(24,2),
    equity_parent          NUMERIC(24,2),
    total_assets           NUMERIC(24,2),
    cash                   NUMERIC(24,2),
    gross_debt             NUMERIC(24,2),
    shares_total           BIGINT,
    shares_outstanding     BIGINT,
    computed_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    dividends_paid_q       NUMERIC(24,2),
    dividends_paid_ttm     NUMERIC(24,2),
    source_doc_id          BIGINT NOT NULL,
    CONSTRAINT pk_company_quarters PRIMARY KEY (issuer_id, period_end),
    CONSTRAINT fk_company_quarters_document FOREIGN KEY (source_doc_id) REFERENCES cvm_documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_company_quarters_issuer FOREIGN KEY (issuer_id) REFERENCES issuers(id)
);
COMMENT ON COLUMN company_quarters.dividends_paid_q IS 'Dividendos + JCP pagos aos acionistas da controladora no trimestre (DFC), positivo. Base do payout.';
COMMENT ON COLUMN company_quarters.dividends_paid_ttm IS 'Dividendos + JCP pagos aos acionistas da controladora em 12 meses (DFC), positivo. Base do payout.';

CREATE INDEX ix_company_quarters_period ON company_quarters (period_end);


-- -----------------------------------------------------------------------------
-- fii_monthly
-- -----------------------------------------------------------------------------
-- Origem: V34__fii_monthly
CREATE TABLE fii_monthly (
    issuer_id             BIGINT NOT NULL,
    reference_month       DATE NOT NULL,
    version               INTEGER NOT NULL,
    net_assets            NUMERIC,
    total_assets          NUMERIC,
    shares_issued         NUMERIC,
    nav_per_share         NUMERIC,
    shareholders          INTEGER,
    admin_fee_month       NUMERIC,
    dividend_yield_month  NUMERIC,
    amortization_month    NUMERIC,
    liquidity_total       NUMERIC,
    total_invested        NUMERIC,
    real_estate_total     NUMERIC,
    real_estate_companies NUMERIC,
    receivables_total     NUMERIC,
    fii_shares_total      NUMERIC,
    liabilities_total     NUMERIC,
    income_to_distribute  NUMERIC,
    imported_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_fii_monthly PRIMARY KEY (issuer_id, reference_month),
    CONSTRAINT fk_fii_monthly_issuer FOREIGN KEY (issuer_id) REFERENCES issuers(id),
    CONSTRAINT ck_fii_monthly_first_day CHECK ((EXTRACT(day FROM reference_month) = (1)))
);

CREATE INDEX ix_fii_monthly_month ON fii_monthly (reference_month);


-- -----------------------------------------------------------------------------
-- user_avatars
-- -----------------------------------------------------------------------------
-- Origem: V42__foto_perfil, V66__user_avatars_constraint_name, V67__constraint_names_english
--
-- [V42] Foto de perfil enviada pelo usuario (docs/planos/PLANO_CADASTRO_USUARIO.md, adendo de 05/10/2026).
-- No banco (decisao A1 do adendo): JPEG 512x512 ja reduzido e sem metadados (~30-80 KB), apagado junto
-- com a conta. Tabela 1:1 propria, e nao coluna em users, para a imagem nao ser carregada a cada leitura
-- do usuario (o filtro, a carteira e as integracoes leem users o tempo todo).
-- users.avatar_url continua sendo a foto do Google (da criacao); a enviada tem precedencia.
CREATE TABLE user_avatars (
    user_id      UUID NOT NULL,
    image        BYTEA NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT user_avatars_pkey PRIMARY KEY (user_id),
    CONSTRAINT fk_user_avatars_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_avatars_content_type CHECK ((content_type = 'image/jpeg')),
    CONSTRAINT ck_user_avatars_size CHECK ((octet_length(image) <= 1048576))
);


-- -----------------------------------------------------------------------------
-- market_indices
-- -----------------------------------------------------------------------------
-- Origem: V43__indices_mercado
CREATE TABLE market_indices (
    id           BIGSERIAL,
    code         VARCHAR(20) NOT NULL,
    name         VARCHAR(100) NOT NULL,
    periodicity  VARCHAR(10) NOT NULL,
    unit         VARCHAR(20) NOT NULL,
    source       VARCHAR(20) NOT NULL,
    source_ref   VARCHAR(40) NOT NULL,
    history_from DATE NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT market_indices_pkey PRIMARY KEY (id),
    CONSTRAINT uk_market_indices_code UNIQUE (code),
    CONSTRAINT uk_market_indices_source UNIQUE (source, source_ref),
    CONSTRAINT ck_market_indices_periodicity CHECK ((periodicity IN ('DIARIA', 'MENSAL'))),
    CONSTRAINT ck_market_indices_source CHECK ((source IN ('BCB_SGS', 'B3'))),
    CONSTRAINT ck_market_indices_unit CHECK ((unit IN ('PCT_DIA', 'PCT_MES', 'PCT_PERIODO', 'PCT_ANO', 'PONTOS', 'BRL')))
);


-- -----------------------------------------------------------------------------
-- market_index_values
-- -----------------------------------------------------------------------------
-- Origem: V43__indices_mercado
CREATE TABLE market_index_values (
    index_id       BIGINT NOT NULL,
    reference_date DATE NOT NULL,
    value          NUMERIC(18,8) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_market_index_values PRIMARY KEY (index_id, reference_date),
    CONSTRAINT fk_market_index_values_index FOREIGN KEY (index_id) REFERENCES market_indices(id)
);


-- -----------------------------------------------------------------------------
-- ceiling_price_quarters
-- -----------------------------------------------------------------------------
-- Origem: V46__preco_teto_modelo_v2, V47__preco_teto_sem_fonte_do_lucro
--
-- [V46] Os quatro lucros do cálculo (T-3 … T), sempre 4 linhas por cálculo.
CREATE TABLE ceiling_price_quarters (
    ceiling_price_id UUID NOT NULL,
    position         SMALLINT NOT NULL,
    period_end       DATE NOT NULL,
    original_value   NUMERIC(24,2) NOT NULL,
    unit             VARCHAR(8) NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    fx_rate          NUMERIC(18,8) NOT NULL,
    value_brl        NUMERIC(24,2) NOT NULL,
    origin           VARCHAR(10) NOT NULL,
    CONSTRAINT pk_ceiling_price_quarters PRIMARY KEY (ceiling_price_id, "position"),
    CONSTRAINT fk_ceiling_price_quarters_cp FOREIGN KEY (ceiling_price_id) REFERENCES ceiling_prices(id) ON DELETE CASCADE,
    CONSTRAINT ck_ceiling_price_quarters_currency CHECK ((currency IN ('BRL', 'USD'))),
    CONSTRAINT ck_ceiling_price_quarters_fx CHECK ((fx_rate > (0))),
    CONSTRAINT ck_ceiling_price_quarters_origin CHECK ((origin IN ('PADRAO', 'INFORMADO'))),
    CONSTRAINT ck_ceiling_price_quarters_position CHECK ((("position" >= 1) AND ("position" <= 4))),
    CONSTRAINT ck_ceiling_price_quarters_unit CHECK ((unit IN ('UNIDADE', 'MIL', 'MILHAO', 'BILHAO')))
);


-- -----------------------------------------------------------------------------
-- notifications
-- -----------------------------------------------------------------------------
-- Origem: V48__notificacoes
CREATE TABLE notifications (
    id         UUID NOT NULL DEFAULT gen_random_uuid(),
    audience   VARCHAR(10) NOT NULL,
    kind       VARCHAR(40) NOT NULL,
    category   VARCHAR(20) NOT NULL,
    severity   VARCHAR(10) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT NOT NULL,
    ref_type   VARCHAR(30),
    ref_id     VARCHAR(64),
    payload    JSONB,
    status     VARCHAR(15),
    status_at  TIMESTAMP,
    status_by  UUID,
    dedup_key  VARCHAR(200),
    user_id    UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_audience CHECK ((((audience = 'USER') AND (user_id IS NOT NULL)) OR ((audience = 'ADMIN') AND (user_id IS NULL)))),
    CONSTRAINT ck_notifications_severity CHECK ((severity IN ('INFO', 'ATENCAO'))),
    CONSTRAINT ck_notifications_status CHECK (((status IS NULL) OR (status IN ('NAO_RESOLVIDA', 'DISPENSADA', 'RESOLVIDA'))))
);

CREATE INDEX idx_notifications_admin ON notifications (created_at DESC) WHERE ((audience)::text = 'ADMIN'::text);
CREATE INDEX idx_notifications_ref ON notifications (ref_type, ref_id);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE UNIQUE INDEX uk_notifications_info_key ON notifications (audience, COALESCE((user_id)::text, ''::text), dedup_key) WHERE ((dedup_key IS NOT NULL) AND (status IS NULL));
CREATE UNIQUE INDEX uk_notifications_open_key ON notifications (audience, COALESCE((user_id)::text, ''::text), dedup_key) WHERE ((dedup_key IS NOT NULL) AND (status IN ('NAO_RESOLVIDA', 'DISPENSADA')));


-- -----------------------------------------------------------------------------
-- notification_states
-- -----------------------------------------------------------------------------
-- Origem: V48__notificacoes
CREATE TABLE notification_states (
    notification_id UUID NOT NULL,
    user_id         UUID NOT NULL,
    read_at         TIMESTAMP,
    archived_at     TIMESTAMP,
    deleted_at      TIMESTAMP,
    CONSTRAINT notification_states_pkey PRIMARY KEY (notification_id, user_id),
    CONSTRAINT fk_notification_states_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_states_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_notification_states_user ON notification_states (user_id);


-- -----------------------------------------------------------------------------
-- fixed_income_tax_rules
-- -----------------------------------------------------------------------------
-- Origem: V52__renda_fixa_ir_iof
--
-- [V52] IR e IOF da renda fixa e do Tesouro Direto (docs/planos/PLANO_VENDA_RENDA_FIXA.md, secao 3.1).
-- O QUE A LEI MUDA FICA AQUI; O JEITO DE CALCULAR FICA NO CODIGO
-- Mesma filosofia da V23 (IR dos proventos). Cada linha diz a aliquota de um imposto
-- para uma faixa de DIAS CORRIDOS desde o aporte, numa vigencia, com a base legal ao
-- lado. O calculo (IOF antes do IR, IR sobre o rendimento menos o IOF, truncar nos
-- centavos, lote a lote) esta em FixedIncomeTaxCalculator e nao muda com a lei.
-- asset_id nulo = regra geral, vale para todo produto de renda fixa e para o Tesouro
-- Direto. asset_id preenchido = excecao do produto (isencao): quando o produto tem
-- linha propria para o imposto, ela substitui a geral inteira.
-- Investidor pessoa fisica residente. Base legal conferida em 15/10/2026 (plano,
-- secao 3.1.1).
-- COMO MUDAR UMA ALIQUOTA (lei nova)
-- Uma migration nova, nunca UPDATE nesta:
--   1. fecha a linha vigente:  UPDATE fixed_income_tax_rules SET valid_to = <vespera> ...
--   2. insere a linha nova:    INSERT ... valid_from = <inicio da vigencia>
-- A vigencia que vale e a da DATA DO RESGATE. Nenhuma faixa pode se sobrepor a outra
-- do mesmo imposto e produto na mesma vigencia (FixedIncomeTaxRuleRepositoryTest).
CREATE TABLE fixed_income_tax_rules (
    id          BIGSERIAL,
    tax         VARCHAR(3) NOT NULL,
    days_from   INTEGER NOT NULL,
    days_to     INTEGER,
    rate        NUMERIC(6,4) NOT NULL,
    valid_from  DATE,
    valid_to    DATE,
    legal_basis VARCHAR(200) NOT NULL,
    asset_id    BIGINT,
    CONSTRAINT fixed_income_tax_rules_pkey PRIMARY KEY (id),
    CONSTRAINT fk_fi_tax_rules_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT ck_fi_tax_rules_days CHECK (((days_from >= 0) AND ((days_to IS NULL) OR (days_to >= days_from)))),
    CONSTRAINT ck_fi_tax_rules_rate CHECK (((rate >= (0)) AND (rate <= (1)))),
    CONSTRAINT ck_fi_tax_rules_tax CHECK ((tax IN ('IR', 'IOF'))),
    CONSTRAINT ck_fi_tax_rules_validity CHECK (((valid_from IS NULL) OR (valid_to IS NULL) OR (valid_from <= valid_to)))
);

CREATE INDEX idx_fi_tax_rules_tax ON fixed_income_tax_rules (tax, asset_id);


-- -----------------------------------------------------------------------------
-- fixed_income_groups
-- -----------------------------------------------------------------------------
-- Origem: V54__emissoras_renda_fixa, V55__seed_emissoras_renda_fixa
--
-- [V54] Grupos: conglomerados prudenciais do BCB, com o nome amigavel ("Nubank")
CREATE TABLE fixed_income_groups (
    id               BIGSERIAL,
    bcb_code         VARCHAR(10),
    name             VARCHAR(120) NOT NULL,
    name_source      VARCHAR(10) NOT NULL,
    description      VARCHAR(255),
    leader_cnpj_root VARCHAR(8),
    logo_ticker      VARCHAR(20),
    extinct          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fixed_income_groups_pkey PRIMARY KEY (id),
    CONSTRAINT uk_fi_groups_bcb_code UNIQUE (bcb_code),
    CONSTRAINT ck_fi_groups_leader CHECK (((leader_cnpj_root IS NULL) OR (leader_cnpj_root ~ '^[0-9]{8}$'))),
    CONSTRAINT ck_fi_groups_name_source CHECK ((name_source IN ('MANUAL', 'AUTOMATICO')))
);
COMMENT ON TABLE fixed_income_groups IS 'Conglomerados prudenciais do BCB com nome amigavel. Ver PLANO_EMISSORAS_RENDA_FIXA.';


-- -----------------------------------------------------------------------------
-- fixed_income_issuers
-- -----------------------------------------------------------------------------
-- Origem: V54__emissoras_renda_fixa, V55__seed_emissoras_renda_fixa
--
-- [V54] Empresas: emissoras e distribuidoras
CREATE TABLE fixed_income_issuers (
    id                BIGSERIAL,
    kind              VARCHAR(25) NOT NULL,
    cnpj_root         VARCHAR(8),
    cnpj              VARCHAR(14),
    legal_name        VARCHAR(255) NOT NULL,
    trade_name        VARCHAR(255),
    segment           VARCHAR(120),
    status            VARCHAR(30),
    website           VARCHAR(255),
    guarantor         VARCHAR(10) NOT NULL DEFAULT 'NENHUM',
    sale_rule         VARCHAR(15) NOT NULL DEFAULT 'PROPORCIONAL',
    group_source      VARCHAR(10),
    sources           VARCHAR(60),
    source_updated_at TIMESTAMP,
    group_id          BIGINT,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fixed_income_issuers_pkey PRIMARY KEY (id),
    CONSTRAINT uk_fi_issuers_cnpj_root UNIQUE (cnpj_root),
    CONSTRAINT fk_fi_issuers_group FOREIGN KEY (group_id) REFERENCES fixed_income_groups(id),
    CONSTRAINT ck_fi_issuers_cnpj CHECK (((cnpj IS NULL) OR (cnpj ~ '^[0-9]{14}$'))),
    CONSTRAINT ck_fi_issuers_cnpj_root CHECK (((cnpj_root IS NULL) OR (cnpj_root ~ '^[0-9]{8}$'))),
    CONSTRAINT ck_fi_issuers_group_source CHECK (((group_source IS NULL) OR (group_source IN ('BCB', 'MANUAL')))),
    CONSTRAINT ck_fi_issuers_guarantor CHECK ((guarantor IN ('FGC', 'FGCOOP', 'NENHUM'))),
    CONSTRAINT ck_fi_issuers_identity CHECK (((cnpj_root IS NOT NULL) OR (kind IN ('TESOURO', 'NAO_LISTADA')))),
    CONSTRAINT ck_fi_issuers_kind CHECK ((kind IN ('BANCO', 'FINANCEIRA', 'COOPERATIVA', 'CORRETORA_DISTRIBUIDORA', 'SECURITIZADORA', 'COMPANHIA', 'TESOURO', 'NAO_LISTADA'))),
    CONSTRAINT ck_fi_issuers_sale_rule CHECK ((sale_rule IN ('PROPORCIONAL', 'UEPS', 'PEPS')))
);
COMMENT ON TABLE fixed_income_issuers IS 'Emissoras e distribuidoras de renda fixa (BCB + CVM). Chave natural: raiz do CNPJ. Ver PLANO_EMISSORAS_RENDA_FIXA.';

CREATE INDEX idx_fi_issuers_group ON fixed_income_issuers (group_id);
CREATE INDEX idx_fi_issuers_kind ON fixed_income_issuers (kind);
CREATE UNIQUE INDEX uk_fi_issuers_fixed_kind ON fixed_income_issuers (kind) WHERE (kind IN ('TESOURO', 'NAO_LISTADA'));


-- -----------------------------------------------------------------------------
-- fixed_income_notes
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V54__emissoras_renda_fixa, V56__titulo_e_posicao_mesmo_ativo
--
-- [V1] O papel de renda fixa do usuário: um CDB comprado no BTG em 2026, vencendo
-- em 2029, a 110% do CDI.
-- É a diferença entre o produto e a aplicação. assets diz "CDB"; esta tabela
-- diz QUAL CDB. Emissor, vencimento e taxa são negociados na compra, variam
-- entre duas aplicações do mesmo produto e interessam a um usuário só — por
-- isso não cabiam no catálogo global, onde apareciam na busca de todos.
-- É daqui que sai a valorização: a taxa contratada está nesta linha, não no
-- ativo genérico.
-- No Tesouro Direto, prazo e forma de remuneração são públicos e vêm de
-- asset_terms; a nota os COPIA no momento do lançamento. A cópia é
-- intencional: o que vale para o cálculo é o que foi contratado, e uma
-- correção futura no catálogo não deve reescrever uma posição já registrada.
--
-- [V54] O titulo: emissora, distribuidora e codigo do papel (L10)
--
-- [V54] codigo do papel na B3 (ex.: CDBA2478FYE), opcional; maiusculo, sem espacos
CREATE TABLE fixed_income_notes (
    id             UUID NOT NULL DEFAULT gen_random_uuid(),
    maturity_date  DATE,
    rate_type      VARCHAR(20),
    indexer        VARCHAR(20),
    indexer_rate   NUMERIC(10,4),
    purchase_date  DATE,
    paper_code     VARCHAR(30),
    user_id        UUID NOT NULL,
    asset_id       BIGINT NOT NULL,
    issuer_id      BIGINT NOT NULL,
    distributor_id BIGINT NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fixed_income_notes_pkey PRIMARY KEY (id),
    CONSTRAINT uk_fixed_income_notes_id_asset UNIQUE (id, asset_id),
    CONSTRAINT fk_fixed_income_notes_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT fk_fixed_income_notes_distributor FOREIGN KEY (distributor_id) REFERENCES fixed_income_issuers(id),
    CONSTRAINT fk_fixed_income_notes_issuer FOREIGN KEY (issuer_id) REFERENCES fixed_income_issuers(id),
    CONSTRAINT fk_fixed_income_notes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_fixed_income_notes_indexer CHECK ((indexer IN ('CDI', 'SELIC', 'IPCA', 'IGPM', 'INPC', 'TR'))),
    CONSTRAINT ck_fixed_income_notes_paper_code CHECK (((paper_code IS NULL) OR (paper_code ~ '^[A-Z0-9]{1,30}$'))),
    CONSTRAINT ck_fixed_income_notes_rate_coherence CHECK ((((rate_type IS NULL) AND (indexer IS NULL)) OR ((rate_type = 'PREFIXADO') AND (indexer IS NULL)) OR ((rate_type IN ('POS_FIXADO', 'HIBRIDO')) AND (indexer IS NOT NULL)))),
    CONSTRAINT ck_fixed_income_notes_rate_type CHECK ((rate_type IN ('PREFIXADO', 'POS_FIXADO', 'HIBRIDO')))
);

CREATE INDEX idx_fixed_income_notes_asset ON fixed_income_notes (asset_id);
CREATE INDEX idx_fixed_income_notes_distributor ON fixed_income_notes (distributor_id);
CREATE INDEX idx_fixed_income_notes_issuer ON fixed_income_notes (issuer_id);
CREATE INDEX idx_fixed_income_notes_maturity ON fixed_income_notes (maturity_date);
CREATE INDEX idx_fixed_income_notes_user ON fixed_income_notes (user_id);
CREATE UNIQUE INDEX uk_fixed_income_notes_paper_code ON fixed_income_notes (user_id, paper_code) WHERE (paper_code IS NOT NULL);
CREATE UNIQUE INDEX uk_fixed_income_notes_purchase ON fixed_income_notes (user_id, asset_id, distributor_id, purchase_date) WHERE (purchase_date IS NOT NULL);


-- -----------------------------------------------------------------------------
-- portfolio_assets
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V56__titulo_e_posicao_mesmo_ativo
--
-- [V1] A posição dentro de uma carteira.
-- NÃO guarda cotas, preço médio nem custo. Eram cópias do que as transações já
-- dizem, mantidas por um recálculo que rodava a cada criação, edição ou
-- exclusão de transação — e qualquer caminho que mexesse em transação sem
-- chamar o recálculo deixava a posição errada. Hoje a posição é apurada na
-- leitura (PositionCalculator), percorrendo as transações em ordem cronológica.
-- A posição é por ativo E papel, não só por ativo. Dois CDBs de bancos
-- diferentes são duas linhas, cada uma com sua taxa e seu vencimento — que é o
-- ponto todo de separar o papel do catálogo. A carteira passa a repetir o mesmo
-- ticker, e a tela mostra emissor e vencimento para que as linhas se distingam.
-- appreciation fica porque NÃO é derivada de transação: é a valorização apurada
-- por fora (cotação, ou a curva da renda fixa quando existir). Valor atual =
-- custo + appreciation. A lógica inverteu-se em relação ao antigo current_value:
-- guardar os dois seria ter duas fontes para o mesmo número.
CREATE TABLE portfolio_assets (
    id             UUID NOT NULL DEFAULT gen_random_uuid(),
    is_reservation BOOLEAN NOT NULL DEFAULT FALSE,
    is_settled     BOOLEAN NOT NULL DEFAULT FALSE,
    appreciation   NUMERIC(18,2) NOT NULL DEFAULT 0,
    portfolio_id   UUID NOT NULL,
    asset_id       BIGINT NOT NULL,
    note_id        UUID,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT portfolio_assets_pkey PRIMARY KEY (id),
    CONSTRAINT uk_portfolio_assets_position UNIQUE NULLS NOT DISTINCT (portfolio_id, asset_id, note_id),
    CONSTRAINT fk_portfolio_assets_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT fk_portfolio_assets_note FOREIGN KEY (note_id, asset_id) REFERENCES fixed_income_notes(id, asset_id) ON UPDATE CASCADE,
    CONSTRAINT fk_portfolio_assets_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE
);

CREATE INDEX idx_portfolio_assets_asset ON portfolio_assets (asset_id);
CREATE INDEX idx_portfolio_assets_note ON portfolio_assets (note_id);
CREATE INDEX idx_portfolio_assets_portfolio ON portfolio_assets (portfolio_id);


-- -----------------------------------------------------------------------------
-- transactions
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V25__integracoes, V50__liquidacao_automatica, V53__venda_renda_fixa_liquido
--
-- [V1] Compras e vendas. São a FONTE DA VERDADE: a posição é derivada delas.
-- shares e price_per_share são anuláveis porque Renda Fixa é lançada por valor
-- total. Um CDB comprado no banco não tem "quantidade x preço unitário" que o
-- usuário conheça: o extrato mostra quanto foi aplicado, e obrigar a digitar
-- 1 x R$ 5.000,00 era inventar uma quantidade. Tesouro Direto continua por
-- quantidade x PU, porque o título é negociado em frações com preço publicado.
-- As regras "renda fixa exige nota" e "renda variável exige quantidade e preço"
-- vivem na APLICAÇÃO. Um CHECK precisaria enxergar assets.asset_type_id a
-- partir daqui, o que só um trigger faz — e trigger para validar é dívida, não
-- garantia.
--
-- [V25] transactions: origem e rastreio da importacao (5.1 item 2, 5.2 item 3, 6.10).
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
-- Importada nasce com external_id e source_snapshot; manual, sem nenhum dos dois.
-- A unicidade (connection_id, external_id) e o que torna a reimportacao
-- idempotente (P4).
--
-- [V50] Liquidação automática do título vencido (docs/planos/PLANO_NOTIFICACOES.md, seção 6; revoga a D8 do plano de
-- valorização da renda fixa). A venda que o sistema cria tem origem própria, SISTEMA: sem external_id nem snapshot
-- da fonte; edited_at marca que o usuário a editou à mão (aí a rotina não a refaz).
--
-- [V53] Venda de renda fixa e do Tesouro pelo bruto e pelo liquido (docs/planos/PLANO_VENDA_RENDA_FIXA.md, secao 3.2).
-- total_amount continua sendo o BRUTO que saiu do titulo (o que a curva e as regras E, F e G usam). Na renda fixa
-- privada ele passa a ser CALCULADO a partir do liquido que o usuario recebeu; no Tesouro continua sendo cotas x
-- PU.
-- net_amount             liquido: digitado (renda fixa) ou calculado (Tesouro: bruto - IR - IOF - taxa)
-- income_tax             IR da venda (soma das partes/lotes), truncado nos centavos
-- iof_tax                IOF da venda, truncado nos centavos
-- settlement_adjustment  so na liquidacao total da renda fixa: bruto - saldo da curva (o "ajuste da liquidacao")
-- Colunas nulas: so valem para venda de renda fixa e de Tesouro (REGRAS 8.1 nao se aplica).
--
-- [V53] Liquido, IR e IOF andam juntos.
CREATE TABLE transactions (
    id                    UUID NOT NULL DEFAULT gen_random_uuid(),
    type                  VARCHAR(20) NOT NULL,
    shares                NUMERIC(18,8),
    price_per_share       NUMERIC(18,8),
    total_amount          NUMERIC(18,2) NOT NULL,
    brokerage_fee         NUMERIC(18,2),
    transaction_date      DATE NOT NULL,
    sale_group_id         UUID,
    is_full_settlement    BOOLEAN NOT NULL DEFAULT FALSE,
    origin                VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    external_id           VARCHAR(200),
    executed_at           TIMESTAMP,
    edited_at             TIMESTAMP,
    source_snapshot       JSONB,
    net_amount            NUMERIC(18,2),
    income_tax            NUMERIC(18,2),
    iof_tax               NUMERIC(18,2),
    settlement_adjustment NUMERIC(18,2),
    portfolio_asset_id    UUID NOT NULL,
    note_id               UUID,
    connection_id         UUID,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT transactions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_transactions_connection FOREIGN KEY (connection_id) REFERENCES integration_connections(id) ON DELETE SET NULL,
    CONSTRAINT fk_transactions_note FOREIGN KEY (note_id) REFERENCES fixed_income_notes(id),
    CONSTRAINT fk_transactions_portfolio_asset FOREIGN KEY (portfolio_asset_id) REFERENCES portfolio_assets(id) ON DELETE CASCADE,
    CONSTRAINT ck_transactions_origin CHECK ((((origin = 'MANUAL') AND (external_id IS NULL) AND (source_snapshot IS NULL) AND (edited_at IS NULL)) OR ((origin = 'AUTO') AND (external_id IS NOT NULL) AND (source_snapshot IS NOT NULL)) OR ((origin = 'SISTEMA') AND (external_id IS NULL) AND (source_snapshot IS NULL)))),
    CONSTRAINT ck_transactions_sale_taxes CHECK ((((net_amount IS NULL) AND (income_tax IS NULL) AND (iof_tax IS NULL)) OR ((net_amount IS NOT NULL) AND (income_tax IS NOT NULL) AND (iof_tax IS NOT NULL)))),
    CONSTRAINT ck_transactions_type CHECK ((type IN ('BUY', 'SELL')))
);

CREATE INDEX idx_transactions_date ON transactions (transaction_date);
CREATE INDEX idx_transactions_note ON transactions (note_id);
CREATE INDEX idx_transactions_portfolio_asset ON transactions (portfolio_asset_id);
CREATE INDEX idx_transactions_sale_group ON transactions (sale_group_id);
CREATE UNIQUE INDEX uk_transactions_external ON transactions (connection_id, external_id) WHERE (connection_id IS NOT NULL);


-- -----------------------------------------------------------------------------
-- dividends
-- -----------------------------------------------------------------------------
-- Origem: V1__schema, V20__proventos_b3, V23__dividend_withholding_tax, V35__amortizacao_rf, V38__catalogo_sem_extrato, V60__limpeza_proventos_indevidos
--
-- [V1] Proventos recebidos: dividendos, JCP e rendimentos de FII.
-- NÃO tem asset_id. O ativo é alcançado pela posição
-- (dividends -> portfolio_assets -> assets), e dois caminhos para o mesmo ativo
-- são duas fontes que podem divergir: nada impediria um provento de PETR4
-- apontar para uma posição de VALE3.
-- Por isso portfolio_asset_id é OBRIGATÓRIO e cascateia: é por ele que o
-- provento chega ao ativo e ao dono. Com ON DELETE SET NULL existiria provento
-- sem ativo e sem dono, invisível até para quem o recebeu.
--
-- [V20] dividends — a conta de cada posicao
-- origin
--   AUTO    criado pelo DividendRecalculator a partir de um evento do catalogo x
--           cotas na data-com. Recalculado quando uma transacao muda.
--   MANUAL  lancado pelo usuario, para provento que nenhuma fonte tem. O
--           recalculador nao mexe nele, e ele nunca vai para o catalogo: o
--           catalogo e global, e dado digitado por um usuario apareceria para
--           todos.
-- As linhas que ja existirem ficam MANUAL: nao tem evento de catalogo por tras e
-- o recalculador nao teria como refaze-las.
--
-- [V20] Amortizacao (FII) e restituicao de capital (empresa) contam como provento e
-- reduzem o custo da posicao (B13).
--
-- [V23] dividends: o que foi retido em cada provento
-- total_amount continua sendo o BRUTO (agora truncado nos centavos, como a B3).
-- withholding_rule_id   regra que calculou; nula = sem regra para o tipo
-- withholding_rate      copia da aliquota aplicada (0 = isento)
-- withholding_tax       IR retido, truncado nos centavos
-- net_amount_per_share  liquido por cota, 8 casas — so exibicao
-- net_amount            TOTAL liquido = total_amount - withholding_tax. Nunca
--                       liquido por cota x cotas: erra 1 centavo (6,15 x 6,16)
-- tax_checked_at        quando a regra foi aplicada. NULA = ainda nao
--                       calculado (diferente de "sem regra"); o
--                       DividendTaxVerifier recalcula esses na subida
--
-- [V23] Com regra, os quatro valores andam juntos; sem regra, nenhum.
CREATE TABLE dividends (
    id                   BIGSERIAL,
    amount_per_share     NUMERIC(18,8) NOT NULL,
    total_amount         NUMERIC(18,2),
    ex_date              DATE NOT NULL,
    payment_date         DATE,
    type                 VARCHAR(20) NOT NULL,
    shares_on_date       NUMERIC(18,8),
    origin               VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    withholding_rate     NUMERIC(6,4),
    withholding_tax      NUMERIC(18,2),
    net_amount_per_share NUMERIC(18,8),
    net_amount           NUMERIC(18,2),
    tax_checked_at       TIMESTAMP,
    portfolio_asset_id   UUID NOT NULL,
    cash_event_id        BIGINT,
    withholding_rule_id  BIGINT,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT dividends_pkey PRIMARY KEY (id),
    CONSTRAINT fk_dividends_cash_event FOREIGN KEY (cash_event_id) REFERENCES asset_cash_events(id),
    CONSTRAINT fk_dividends_portfolio_asset FOREIGN KEY (portfolio_asset_id) REFERENCES portfolio_assets(id) ON DELETE CASCADE,
    CONSTRAINT fk_dividends_withholding_rule FOREIGN KEY (withholding_rule_id) REFERENCES withholding_tax_rules(id),
    CONSTRAINT ck_dividends_auto_has_event CHECK (((origin = 'MANUAL') OR (cash_event_id IS NOT NULL))),
    CONSTRAINT ck_dividends_origin CHECK ((origin IN ('AUTO', 'MANUAL'))),
    CONSTRAINT ck_dividends_type CHECK ((type IN ('DIVIDENDO', 'JCP', 'RENDIMENTO', 'AMORTIZACAO', 'RESTITUICAO_CAPITAL'))),
    CONSTRAINT ck_dividends_withholding CHECK ((((withholding_rule_id IS NULL) AND (withholding_rate IS NULL) AND (withholding_tax IS NULL) AND (net_amount_per_share IS NULL) AND (net_amount IS NULL)) OR ((withholding_rule_id IS NOT NULL) AND (withholding_rate IS NOT NULL) AND (withholding_tax IS NOT NULL) AND (net_amount_per_share IS NOT NULL) AND (net_amount IS NOT NULL))))
);

CREATE INDEX idx_dividends_cash_event ON dividends (cash_event_id);
CREATE INDEX idx_dividends_ex_date ON dividends (ex_date);
CREATE INDEX idx_dividends_portfolio_asset ON dividends (portfolio_asset_id);
CREATE INDEX idx_dividends_tax_pending ON dividends (portfolio_asset_id) WHERE (tax_checked_at IS NULL);
CREATE UNIQUE INDEX uk_dividends_position_event ON dividends (portfolio_asset_id, cash_event_id) WHERE (cash_event_id IS NOT NULL);


-- -----------------------------------------------------------------------------
-- integration_run_items
-- -----------------------------------------------------------------------------
-- Origem: V25__integracoes, V26__integracoes_confirmacao
--
-- [V25] Itens da execucao: SO o que nao entrou direto — e o "nao mapeadas" do
-- levantamento, persistido. O que foi importado ja esta em transactions.
-- outcome  UNMAPPED            regra R6/R7/R8: sem valor em reais, ticker fora
--                              do catalogo, taxa em outro ativo (PENDENCIAS 15)
--          POSSIBLE_DUPLICATE  parece com um lancamento manual (6.6); aguarda
--                              a decisao do usuario
--          FAILED              erro ao gravar (regra de negocio recusou)
-- raw      o movimento como veio da fonte, para auditoria e reprocessamento
--
-- [V26] integration_run_items deixa de guardar so o que nao entrou: guarda o lote
-- inteiro. raw passa a ser o movimento normalizado (ImportedMovement em JSON,
-- que carrega o bruto da fonte dentro), para a confirmacao reaplicar as regras.
--
-- [V26] duplicate_of    o lancamento MANUAL que parece ser esta mesma operacao (6.6).
--                 SET NULL: se o usuario apagar o manual antes de confirmar, a
--                 duvida some junto.
-- resolution      o que a confirmacao fez com o item:
--                 IMPORTED   virou transacao
--                 REPLACED   virou transacao e o manual duplicado saiu
--                 KEPT_BOTH  virou transacao e o manual ficou
--                 SKIPPED    nao entrou (usuario ignorou a duplicata, ou ja
--                            tinha sido importado/excluido nesse meio tempo)
-- transaction_id  a transacao criada, quando houve
CREATE TABLE integration_run_items (
    id             UUID NOT NULL DEFAULT gen_random_uuid(),
    external_id    VARCHAR(200) NOT NULL,
    outcome        VARCHAR(30) NOT NULL,
    reason         VARCHAR(500),
    raw            JSONB NOT NULL,
    resolution     VARCHAR(20),
    run_id         UUID NOT NULL,
    duplicate_of   UUID,
    transaction_id UUID,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT integration_run_items_pkey PRIMARY KEY (id),
    CONSTRAINT uk_integration_run_items UNIQUE (run_id, external_id),
    CONSTRAINT fk_integration_run_items_duplicate FOREIGN KEY (duplicate_of) REFERENCES transactions(id) ON DELETE SET NULL,
    CONSTRAINT fk_integration_run_items_run FOREIGN KEY (run_id) REFERENCES integration_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_integration_run_items_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE SET NULL,
    CONSTRAINT ck_integration_run_items_outcome CHECK ((outcome IN ('NEW', 'EXISTING', 'EXCLUDED', 'UNMAPPED', 'POSSIBLE_DUPLICATE', 'FAILED'))),
    CONSTRAINT ck_integration_run_items_resolution CHECK (((resolution IS NULL) OR (resolution IN ('IMPORTED', 'REPLACED', 'KEPT_BOTH', 'SKIPPED'))))
);


-- -----------------------------------------------------------------------------
-- stock_event_costs
-- -----------------------------------------------------------------------------
-- Origem: V51__custo_bonificacao
--
-- [V51] Custo da bonificação informado pelo usuário (docs/planos/PLANO_NOTIFICACOES.md, 3.4 e 5.4; PENDENCIAS 13.2).
-- A B3 não informa o custo atribuído às ações bonificadas (remarks vazio nos casos reais): elas entram com custo
-- zero provisório. O usuário informa o valor por ação do fato relevante e ele vale só para a posição dele — dado
-- de
-- usuário, gravado pela app-api; asset_stock_events.assigned_cost (catálogo) não é tocado.
CREATE TABLE stock_event_costs (
    portfolio_asset_id UUID NOT NULL,
    stock_event_id     BIGINT NOT NULL,
    cost_per_share     NUMERIC(18,8) NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT stock_event_costs_pkey PRIMARY KEY (portfolio_asset_id, stock_event_id),
    CONSTRAINT fk_stock_event_costs_event FOREIGN KEY (stock_event_id) REFERENCES asset_stock_events(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_event_costs_position FOREIGN KEY (portfolio_asset_id) REFERENCES portfolio_assets(id) ON DELETE CASCADE,
    CONSTRAINT ck_stock_event_costs_positive CHECK ((cost_per_share > (0)))
);


-- -----------------------------------------------------------------------------
-- benchmarks
-- -----------------------------------------------------------------------------
-- Origem: V57__benchmarks
CREATE TABLE benchmarks (
    id               BIGSERIAL,
    code             VARCHAR(30) NOT NULL,
    name             VARCHAR(100) NOT NULL,
    category         VARCHAR(20) NOT NULL,
    kind             VARCHAR(10) NOT NULL,
    accepts_spread   BOOLEAN NOT NULL DEFAULT FALSE,
    note             VARCHAR(400),
    default_selected BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order       INTEGER NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    market_index_id  BIGINT,
    asset_id         BIGINT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT benchmarks_pkey PRIMARY KEY (id),
    CONSTRAINT uk_benchmarks_code UNIQUE (code),
    CONSTRAINT fk_benchmarks_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT fk_benchmarks_index FOREIGN KEY (market_index_id) REFERENCES market_indices(id),
    CONSTRAINT ck_benchmarks_category CHECK ((category IN ('RENDA_FIXA', 'JUROS', 'INFLACAO', 'ACOES_BR', 'FII', 'ACOES_EUA', 'CRIPTO'))),
    CONSTRAINT ck_benchmarks_kind CHECK ((((kind = 'INDICE') AND (market_index_id IS NOT NULL) AND (asset_id IS NULL)) OR ((kind = 'ATIVO') AND (asset_id IS NOT NULL) AND (market_index_id IS NULL))))
);


-- -----------------------------------------------------------------------------
-- treasury_offers
-- -----------------------------------------------------------------------------
-- Origem: V59__treasury_offers
CREATE TABLE treasury_offers (
    asset_id   BIGINT NOT NULL,
    rate_date  DATE NOT NULL,
    buy_rate   NUMERIC(8,4),
    sell_rate  NUMERIC(8,4),
    on_sale    BOOLEAN NOT NULL DEFAULT FALSE,
    sale_date  DATE,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT treasury_offers_pkey PRIMARY KEY (asset_id),
    CONSTRAINT fk_treasury_offers_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE
);


-- -----------------------------------------------------------------------------
-- task_types
-- -----------------------------------------------------------------------------
-- Origem: V61__tasks, V62__seed_tasks, V63__task_types_stoppable
CREATE TABLE task_types (
    id          BIGSERIAL,
    type        VARCHAR(80) NOT NULL,
    description TEXT NOT NULL,
    service     VARCHAR(10) NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    stoppable   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT task_types_pkey PRIMARY KEY (id),
    CONSTRAINT uk_task_types_type UNIQUE (type),
    CONSTRAINT ck_task_types_service CHECK ((service IN ('ADM_API', 'APP_API'))),
    CONSTRAINT ck_task_types_type CHECK ((type ~ '^[A-Z0-9]+(_[A-Z0-9]+)*$'))
);


-- -----------------------------------------------------------------------------
-- task_type_attributes
-- -----------------------------------------------------------------------------
-- Origem: V61__tasks, V62__seed_tasks, V64__task_type_attributes_position
CREATE TABLE task_type_attributes (
    id           BIGSERIAL,
    att_name     VARCHAR(80) NOT NULL,
    att_value    TEXT,
    value_type   VARCHAR(10) NOT NULL,
    required     BOOLEAN NOT NULL DEFAULT FALSE,
    description  TEXT NOT NULL,
    position     SMALLINT NOT NULL DEFAULT 0,
    task_type_id BIGINT NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT task_type_attributes_pkey PRIMARY KEY (id),
    CONSTRAINT uk_task_type_attributes UNIQUE (task_type_id, att_name),
    CONSTRAINT fk_task_type_attributes_type FOREIGN KEY (task_type_id) REFERENCES task_types(id) ON DELETE CASCADE,
    CONSTRAINT ck_task_type_attributes_name CHECK ((att_name ~ '^[A-Z0-9]+(_[A-Z0-9]+)*$')),
    CONSTRAINT ck_task_type_attributes_value_type CHECK ((value_type IN ('YEAR', 'DATE', 'BOOLEAN', 'INTEGER', 'TEXT', 'LIST')))
);


-- -----------------------------------------------------------------------------
-- tasks
-- -----------------------------------------------------------------------------
-- Origem: V61__tasks, V62__seed_tasks, V65__tasks_run_on_startup
CREATE TABLE tasks (
    id             BIGSERIAL,
    name           VARCHAR(80) NOT NULL,
    description    TEXT,
    cron           VARCHAR(120),
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    run_on_startup BOOLEAN NOT NULL DEFAULT FALSE,
    task_type_id   BIGINT NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMP,
    CONSTRAINT tasks_pkey PRIMARY KEY (id),
    CONSTRAINT fk_tasks_type FOREIGN KEY (task_type_id) REFERENCES task_types(id),
    CONSTRAINT ck_tasks_name CHECK ((name ~ '^[A-Z0-9]+(_[A-Z0-9]+)*$'))
);

CREATE INDEX idx_tasks_type ON tasks (task_type_id);
CREATE UNIQUE INDEX uk_tasks_name ON tasks (name) WHERE (deleted_at IS NULL);


-- -----------------------------------------------------------------------------
-- task_attributes
-- -----------------------------------------------------------------------------
-- Origem: V61__tasks, V62__seed_tasks
CREATE TABLE task_attributes (
    id                     BIGSERIAL,
    att_value              TEXT,
    task_id                BIGINT NOT NULL,
    task_type_attribute_id BIGINT NOT NULL,
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT task_attributes_pkey PRIMARY KEY (id),
    CONSTRAINT uk_task_attributes UNIQUE (task_id, task_type_attribute_id),
    CONSTRAINT fk_task_attributes_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_attributes_type_attribute FOREIGN KEY (task_type_attribute_id) REFERENCES task_type_attributes(id) ON DELETE CASCADE
);


-- -----------------------------------------------------------------------------
-- task_executions
-- -----------------------------------------------------------------------------
-- Origem: V61__tasks
CREATE TABLE task_executions (
    id                BIGSERIAL,
    status            VARCHAR(12) NOT NULL,
    trigger_type      VARCHAR(10) NOT NULL,
    scheduled_for     TIMESTAMP NOT NULL,
    started_at        TIMESTAMP NOT NULL,
    finished_at       TIMESTAMP,
    stop_requested_at TIMESTAMP,
    parameters        JSONB,
    summary           TEXT,
    error_message     TEXT,
    log_lines_omitted INTEGER NOT NULL DEFAULT 0,
    task_id           BIGINT NOT NULL,
    requested_by      UUID,
    stop_requested_by UUID,
    CONSTRAINT task_executions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_task_executions_requested_by FOREIGN KEY (requested_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_task_executions_stop_by FOREIGN KEY (stop_requested_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_task_executions_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT ck_task_executions_finished CHECK (((status = 'RUNNING') = (finished_at IS NULL))),
    CONSTRAINT ck_task_executions_status CHECK ((status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'SKIPPED'))),
    CONSTRAINT ck_task_executions_trigger CHECK ((trigger_type IN ('SCHEDULE', 'MANUAL', 'STARTUP')))
);

CREATE INDEX idx_task_executions_running ON task_executions (task_id) WHERE ((status)::text = 'RUNNING'::text);
CREATE INDEX idx_task_executions_started ON task_executions (started_at DESC);
CREATE INDEX idx_task_executions_task ON task_executions (task_id, started_at DESC);


-- -----------------------------------------------------------------------------
-- task_execution_logs
-- -----------------------------------------------------------------------------
-- Origem: V61__tasks
CREATE TABLE task_execution_logs (
    id           BIGSERIAL,
    logged_at    TIMESTAMP NOT NULL,
    level        VARCHAR(5) NOT NULL,
    message      TEXT NOT NULL,
    execution_id BIGINT NOT NULL,
    CONSTRAINT task_execution_logs_pkey PRIMARY KEY (id),
    CONSTRAINT fk_task_execution_logs_execution FOREIGN KEY (execution_id) REFERENCES task_executions(id) ON DELETE CASCADE,
    CONSTRAINT ck_task_execution_logs_level CHECK ((level IN ('INFO', 'WARN', 'ERROR')))
);

CREATE INDEX idx_task_execution_logs_execution ON task_execution_logs (execution_id, id);
CREATE INDEX idx_task_execution_logs_logged ON task_execution_logs (logged_at);
