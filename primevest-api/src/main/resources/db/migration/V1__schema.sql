-- =============================================================================
-- PrimeVest — schema completo.
--
-- Este arquivo descreve o RESULTADO, não o caminho. As 30 migrations
-- anteriores contavam a história do projeto — criavam colunas que depois eram
-- derrubadas, classificavam ativos em quatro etapas, tornavam campos anuláveis
-- meses depois. Nada disso interessa a um banco novo, e cada passo intermediário
-- era mais uma coisa que podia falhar numa subida limpa.
--
-- A ORDEM DAS TABELAS É A ORDEM DAS CHAVES ESTRANGEIRAS, não alfabética:
--   users -> asset_types -> asset_categories -> assets -> asset_terms
--   -> portfolios -> fixed_income_notes -> portfolio_assets -> transactions
--   -> dividends -> asset_price_history -> strategies -> ceiling_prices -> goals
--
-- fixed_income_notes precisa vir antes de portfolio_assets e transactions,
-- porque as duas referenciam a nota.
--
-- -----------------------------------------------------------------------------
-- A DECISÃO ESTRUTURAL QUE EXPLICA O RESTO
-- -----------------------------------------------------------------------------
-- O catálogo é global; o papel é do usuário.
--
--   assets              o instrumento: ticker, nome, tipo, categoria. Sem
--                       user_id de propósito — um ativo é o mesmo para todos.
--   asset_terms         parâmetros PÚBLICOS do papel, 1:1 com assets. Hoje só
--                       Tesouro Direto.
--   fixed_income_notes  O PAPEL: emissor, vencimento, forma de remuneração e a
--                       taxa contratada. Pertence a um usuário.
--
-- Renda fixa não cabia só no catálogo: um CDB não tem vencimento nem taxa
-- próprios — cada aplicação negocia os seus com o banco, no dia da compra.
-- Enquanto essas colunas viviam em assets, registrar dois CDBs de bancos
-- diferentes exigia criar dois ativos ("CDB BTG 2027", "CDB Inter 2028") num
-- catálogo global, que aparecia na busca de todo mundo.
--
-- Convenções:
--   - UUID nas tabelas de dados do usuário; BIGSERIAL no catálogo.
--   - Quantidades e preços unitários em NUMERIC(18,8); as 8 casas existem por
--     causa de cripto, e alinhar para 4 truncaria fração de BTC.
--   - Valores monetários agregados em NUMERIC(18,2).
--   - Toda coluna de data de auditoria é NOT NULL DEFAULT NOW(). O DEFAULT não
--     é enfeite: o preenchimento no Java é manual (@PrePersist / @PreUpdate,
--     sem @CreationTimestamp), então um INSERT feito por SQL direto (seed,
--     migration, correção via psql) não passa pelo Hibernate e depende dele.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- users
-- -----------------------------------------------------------------------------
-- Autenticação por email/senha (password_hash) ou Google (google_id).
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    google_id     VARCHAR(255),
    avatar_url    VARCHAR(512),
    is_admin      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email     UNIQUE (email),
    CONSTRAINT uk_users_google_id UNIQUE (google_id)
);

CREATE INDEX idx_users_email     ON users (email);
CREATE INDEX idx_users_google_id ON users (google_id);


-- -----------------------------------------------------------------------------
-- asset_types
-- -----------------------------------------------------------------------------
-- Tipos de ativo (Ações, FII, Tesouro Direto...). Tabela de referência.
--
-- price_source diz de ONDE vem a cotação daquele tipo. Existe para que a
-- escolha do provedor seja dado, e não comparação de string no código: antes
-- o scheduler fazia "CRYPTO".equals(name), o que quebrava silenciosamente a
-- cada renomeação ou tipo novo.
--   BRAPI      cotação da B3
--   COINGECKO  criptomoedas
--   MANUAL     sem provedor automático (renda fixa, fundos, ativos dos EUA
--              enquanto não houver cliente para eles)
--
-- fixed_income marca os tipos que têm remuneração contratada — os que abrem o
-- quadro do título no lançamento e geram uma linha em fixed_income_notes. É
-- coluna, e não lista de nomes no código, pela mesma razão de price_source: a
-- tela e o service só perguntam à flag.
CREATE TABLE asset_types (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(255),
    color_hex    VARCHAR(255),
    price_source VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    fixed_income BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_asset_types_name UNIQUE (name),
    CONSTRAINT ck_asset_types_price_source CHECK (price_source IN ('BRAPI', 'COINGECKO', 'MANUAL'))
);


-- -----------------------------------------------------------------------------
-- asset_categories
-- -----------------------------------------------------------------------------
-- Categorias dentro de cada tipo de ativo (CDB, Tijolo, Papel...).
--
-- segment é o segundo nível, e o que ele significa depende do tipo:
--   FII    tipo de fundo > segmento
--   AÇÃO   setor B3 > segmento B3
--   demais nulo
CREATE TABLE asset_categories (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    description   VARCHAR(255),
    asset_type_id BIGINT       NOT NULL,
    segment       VARCHAR(255),
    CONSTRAINT fk_asset_categories_type  FOREIGN KEY (asset_type_id) REFERENCES asset_types (id),
    -- Uma categoria e o par (name, segment) dentro do tipo, por isso a unicidade
    -- inclui segment. NULLS NOT DISTINCT (PG 15+) mantem a regra para os tipos
    -- sem segmento: dois 'CDB' com segment nulo continuam proibidos.
    CONSTRAINT uk_asset_categories_scope UNIQUE NULLS NOT DISTINCT (asset_type_id, name, segment)
);

CREATE INDEX idx_asset_categories_type ON asset_categories (asset_type_id);


-- -----------------------------------------------------------------------------
-- assets
-- -----------------------------------------------------------------------------
-- Catálogo de ativos: o que o instrumento É.
--
-- Nada aqui descreve uma compra. Vencimento e remuneração nunca moram nesta
-- tabela: os que são públicos e do papel (Tesouro Direto) ficam em
-- asset_terms, e os de cada aplicação (CDB, LCI, debênture) em
-- fixed_income_notes, que pertence ao usuário.
--
-- O catálogo não tem user_id de propósito: um ativo é o mesmo para todos.
CREATE TABLE assets (
    id                BIGSERIAL    PRIMARY KEY,
    ticker            VARCHAR(255) NOT NULL,
    name              VARCHAR(255) NOT NULL,
    asset_type_id     BIGINT       NOT NULL,
    asset_category_id BIGINT,
    current_price     NUMERIC(18,8),
    price_updated_at  TIMESTAMP,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_assets_ticker   UNIQUE (ticker),
    CONSTRAINT fk_assets_type     FOREIGN KEY (asset_type_id)     REFERENCES asset_types (id),
    CONSTRAINT fk_assets_category FOREIGN KEY (asset_category_id) REFERENCES asset_categories (id)
);

CREATE INDEX idx_assets_ticker   ON assets (ticker);
CREATE INDEX idx_assets_type     ON assets (asset_type_id);
CREATE INDEX idx_assets_category ON assets (asset_category_id);


-- -----------------------------------------------------------------------------
-- asset_terms
-- -----------------------------------------------------------------------------
-- Parâmetros públicos de um título, 1:1 com assets.
--
-- Hoje só o Tesouro Direto preenche isto. "Tesouro IPCA+ 2029" vence em
-- 15/05/2029 e remunera IPCA mais spread para qualquer investidor: é
-- característica do papel, e por isso fica no catálogo. Um CDB não tem nada
-- disso — cada aplicação negocia o seu, e essa informação mora em
-- fixed_income_notes.
--
-- Tabela separada, e não colunas em assets, porque 130 dos 1264 ativos têm
-- parâmetros e os outros 1134 carregariam quatro colunas nulas.
--
-- Sem id próprio: a chave é o ativo. Um BIGSERIAL aqui só abriria espaço para
-- duas linhas de parâmetros para o mesmo papel. E ativo sem parâmetros não
-- ganha linha vazia: a ausência da linha JÁ é a informação "não tem prazo nem
-- indexador".
--
-- A taxa NÃO está aqui de propósito. No Tesouro ela é do dia da compra, não do
-- título: dois investidores levam o mesmo papel a IPCA + 6,2% e IPCA + 5,8%
-- conforme quando compraram. Taxa é da nota.
CREATE TABLE asset_terms (
    asset_id      BIGINT PRIMARY KEY,
    maturity_date DATE,
    rate_type     VARCHAR(20),
    indexer       VARCHAR(20),
    CONSTRAINT fk_asset_terms_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE,
    CONSTRAINT ck_asset_terms_rate_type CHECK (rate_type IN ('PREFIXADO', 'POS_FIXADO', 'HIBRIDO')),
    CONSTRAINT ck_asset_terms_indexer   CHECK (indexer   IN ('CDI', 'SELIC', 'IPCA', 'IGPM', 'INPC', 'TR')),
    -- Prefixado não tem índice; pós-fixado e híbrido exigem um. Sem rate_type
    -- não dá para distinguir "110% do CDI" de "CDI + 2%": os dois teriam
    -- indexer = CDI, com sentidos completamente diferentes.
    CONSTRAINT ck_asset_terms_rate_coherence CHECK (
        (rate_type IS NULL          AND indexer IS NULL)
        OR (rate_type = 'PREFIXADO' AND indexer IS NULL)
        OR (rate_type IN ('POS_FIXADO', 'HIBRIDO') AND indexer IS NOT NULL)
    )
);

CREATE INDEX idx_asset_terms_maturity ON asset_terms (maturity_date);


-- -----------------------------------------------------------------------------
-- portfolios
-- -----------------------------------------------------------------------------
-- Carteira do usuário, uma por usuário.
--
-- NÃO guarda totais. total_invested, current_value e total_dividends existiram
-- e saíram: eram cópias do que as posições já dizem, e só ficavam certas
-- enquanto alguém lembrasse de recalculá-las. O resumo da carteira soma as
-- posições na leitura.
CREATE TABLE portfolios (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_portfolios_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_portfolios_user ON portfolios (user_id);


-- -----------------------------------------------------------------------------
-- fixed_income_notes
-- -----------------------------------------------------------------------------
-- O papel de renda fixa do usuário: um CDB comprado no BTG em 2026, vencendo
-- em 2029, a 110% do CDI.
--
-- É a diferença entre o produto e a aplicação. assets diz "CDB"; esta tabela
-- diz QUAL CDB. Emissor, vencimento e taxa são negociados na compra, variam
-- entre duas aplicações do mesmo produto e interessam a um usuário só — por
-- isso não cabiam no catálogo global, onde apareciam na busca de todos.
--
-- É daqui que sai a valorização: a taxa contratada está nesta linha, não no
-- ativo genérico.
--
-- No Tesouro Direto, prazo e forma de remuneração são públicos e vêm de
-- asset_terms; a nota os COPIA no momento do lançamento. A cópia é
-- intencional: o que vale para o cálculo é o que foi contratado, e uma
-- correção futura no catálogo não deve reescrever uma posição já registrada.
CREATE TABLE fixed_income_notes (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL,
    asset_id      BIGINT        NOT NULL,
    -- Texto livre por enquanto: virar tabela de emissores é uma decisão que
    -- depende de saber quais nomes o usuário realmente digita, e essa lista só
    -- existe depois de um tempo de uso.
    issuer        VARCHAR(255),
    maturity_date DATE,
    rate_type     VARCHAR(20),
    indexer       VARCHAR(20),
    -- O significado é dado por rate_type: % a.a. num prefixado, % do índice num
    -- pós-fixado, spread num híbrido.
    indexer_rate  NUMERIC(10,4),
    -- No Tesouro Direto é ela que ordena o FIFO ("as vendas saem dos títulos
    -- mais antigos primeiro"). Nula na Renda Fixa, onde o mesmo título recebe
    -- vários aportes, inclusive no mesmo dia, e o saldo é por valor e não por
    -- cota — por isso o índice único abaixo é PARCIAL.
    purchase_date DATE,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_fixed_income_notes_user  FOREIGN KEY (user_id)  REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_fixed_income_notes_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT ck_fixed_income_notes_rate_type CHECK (rate_type IN ('PREFIXADO', 'POS_FIXADO', 'HIBRIDO')),
    CONSTRAINT ck_fixed_income_notes_indexer   CHECK (indexer   IN ('CDI', 'SELIC', 'IPCA', 'IGPM', 'INPC', 'TR')),
    CONSTRAINT ck_fixed_income_notes_rate_coherence CHECK (
        (rate_type IS NULL          AND indexer IS NULL)
        OR (rate_type = 'PREFIXADO' AND indexer IS NULL)
        OR (rate_type IN ('POS_FIXADO', 'HIBRIDO') AND indexer IS NOT NULL)
    )
);

CREATE INDEX idx_fixed_income_notes_user     ON fixed_income_notes (user_id);
CREATE INDEX idx_fixed_income_notes_asset    ON fixed_income_notes (asset_id);
CREATE INDEX idx_fixed_income_notes_maturity ON fixed_income_notes (maturity_date);

-- Um ativo do Tesouro não tem dois títulos comprados no mesmo dia: se o usuário
-- comprar de novo na mesma data, o certo é alterar a quantidade da compra que
-- já existe. Parcial porque a regra só vale onde a data existe.
CREATE UNIQUE INDEX uk_fixed_income_notes_purchase
    ON fixed_income_notes (user_id, asset_id, purchase_date)
    WHERE purchase_date IS NOT NULL;


-- -----------------------------------------------------------------------------
-- portfolio_assets
-- -----------------------------------------------------------------------------
-- A posição dentro de uma carteira.
--
-- NÃO guarda cotas, preço médio nem custo. Eram cópias do que as transações já
-- dizem, mantidas por um recálculo que rodava a cada criação, edição ou
-- exclusão de transação — e qualquer caminho que mexesse em transação sem
-- chamar o recálculo deixava a posição errada. Hoje a posição é apurada na
-- leitura (PositionCalculator), percorrendo as transações em ordem cronológica.
--
-- A posição é por ativo E papel, não só por ativo. Dois CDBs de bancos
-- diferentes são duas linhas, cada uma com sua taxa e seu vencimento — que é o
-- ponto todo de separar o papel do catálogo. A carteira passa a repetir o mesmo
-- ticker, e a tela mostra emissor e vencimento para que as linhas se distingam.
--
-- appreciation fica porque NÃO é derivada de transação: é a valorização apurada
-- por fora (cotação, ou a curva da renda fixa quando existir). Valor atual =
-- custo + appreciation. A lógica inverteu-se em relação ao antigo current_value:
-- guardar os dois seria ter duas fontes para o mesmo número.
CREATE TABLE portfolio_assets (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id   UUID          NOT NULL,
    asset_id       BIGINT        NOT NULL,
    -- Nulo para renda variável: uma ação não tem emissor nem taxa contratada.
    note_id        UUID,
    is_reservation BOOLEAN       NOT NULL DEFAULT FALSE,
    -- Posição encerrada por vencimento do título ou resgate de renda fixa.
    is_settled     BOOLEAN       NOT NULL DEFAULT FALSE,
    appreciation   NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_portfolio_assets_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_assets_asset     FOREIGN KEY (asset_id)     REFERENCES assets (id),
    CONSTRAINT fk_portfolio_assets_note      FOREIGN KEY (note_id)      REFERENCES fixed_income_notes (id),
    -- NULLS NOT DISTINCT (PG 15+) é o que faz a regra valer para renda
    -- variável: no padrão SQL dois NULLs nunca são iguais, então
    -- (carteira, PETR4, NULL) não conflitaria com outro igual e a unicidade de
    -- sempre simplesmente deixaria de existir para ações e FIIs.
    CONSTRAINT uk_portfolio_assets_position  UNIQUE NULLS NOT DISTINCT (portfolio_id, asset_id, note_id)
);

CREATE INDEX idx_portfolio_assets_portfolio ON portfolio_assets (portfolio_id);
CREATE INDEX idx_portfolio_assets_asset     ON portfolio_assets (asset_id);
CREATE INDEX idx_portfolio_assets_note      ON portfolio_assets (note_id);


-- -----------------------------------------------------------------------------
-- transactions
-- -----------------------------------------------------------------------------
-- Compras e vendas. São a FONTE DA VERDADE: a posição é derivada delas.
--
-- shares e price_per_share são anuláveis porque Renda Fixa é lançada por valor
-- total. Um CDB comprado no banco não tem "quantidade x preço unitário" que o
-- usuário conheça: o extrato mostra quanto foi aplicado, e obrigar a digitar
-- 1 x R$ 5.000,00 era inventar uma quantidade. Tesouro Direto continua por
-- quantidade x PU, porque o título é negociado em frações com preço publicado.
--
-- As regras "renda fixa exige nota" e "renda variável exige quantidade e preço"
-- vivem na APLICAÇÃO. Um CHECK precisaria enxergar assets.asset_type_id a
-- partir daqui, o que só um trigger faz — e trigger para validar é dívida, não
-- garantia.
CREATE TABLE transactions (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_asset_id UUID          NOT NULL,
    -- O papel movimentado. Nulo para renda variável.
    note_id            UUID,
    type               VARCHAR(20)   NOT NULL,
    shares             NUMERIC(18,8),
    price_per_share    NUMERIC(18,8),
    total_amount       NUMERIC(18,2) NOT NULL,
    brokerage_fee      NUMERIC(18,2),
    transaction_date   DATE          NOT NULL,
    -- No Tesouro o saldo é do ATIVO, mas a posição é do TÍTULO: uma venda de 5
    -- cotas pode consumir dois títulos comprados em datas diferentes. Cada parte
    -- vira uma transação presa ao título que ela liquida, e o grupo é o que diz
    -- que as duas partes são a MESMA venda. Sem ele, "a venda de 5 cotas"
    -- deixaria de existir como objeto: seriam duas linhas soltas, editáveis em
    -- separado, e bastaria mexer numa para furar o FIFO.
    -- Nulo em compra e em venda de ativo cujo saldo não é por título.
    sale_group_id      UUID,
    -- Liquidação total de uma venda de Renda Fixa. Fica na transação porque a
    -- regra precisa dela duas vezes: para reabrir o formulário no estado certo,
    -- e para reavaliar a venda quando ela é editada. O is_settled da posição é
    -- derivado daqui.
    is_full_settlement BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_transactions_portfolio_asset FOREIGN KEY (portfolio_asset_id) REFERENCES portfolio_assets (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_note            FOREIGN KEY (note_id)            REFERENCES fixed_income_notes (id),
    CONSTRAINT ck_transactions_type CHECK (type IN ('BUY', 'SELL'))
);

CREATE INDEX idx_transactions_portfolio_asset ON transactions (portfolio_asset_id);
CREATE INDEX idx_transactions_note            ON transactions (note_id);
CREATE INDEX idx_transactions_date            ON transactions (transaction_date);
CREATE INDEX idx_transactions_sale_group      ON transactions (sale_group_id);


-- -----------------------------------------------------------------------------
-- dividends
-- -----------------------------------------------------------------------------
-- Proventos recebidos: dividendos, JCP e rendimentos de FII.
--
-- NÃO tem asset_id. O ativo é alcançado pela posição
-- (dividends -> portfolio_assets -> assets), e dois caminhos para o mesmo ativo
-- são duas fontes que podem divergir: nada impediria um provento de PETR4
-- apontar para uma posição de VALE3.
--
-- Por isso portfolio_asset_id é OBRIGATÓRIO e cascateia: é por ele que o
-- provento chega ao ativo e ao dono. Com ON DELETE SET NULL existiria provento
-- sem ativo e sem dono, invisível até para quem o recebeu.
CREATE TABLE dividends (
    id                 BIGSERIAL     PRIMARY KEY,
    portfolio_asset_id UUID          NOT NULL,
    amount_per_share   NUMERIC(18,8) NOT NULL,
    total_amount       NUMERIC(18,2),
    ex_date            DATE          NOT NULL,
    payment_date       DATE,
    type               VARCHAR(20)   NOT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_dividends_portfolio_asset FOREIGN KEY (portfolio_asset_id) REFERENCES portfolio_assets (id) ON DELETE CASCADE,
    CONSTRAINT ck_dividends_type CHECK (type IN ('DIVIDENDO', 'JCP', 'RENDIMENTO'))
);

CREATE INDEX idx_dividends_portfolio_asset ON dividends (portfolio_asset_id);
CREATE INDEX idx_dividends_ex_date         ON dividends (ex_date);


-- -----------------------------------------------------------------------------
-- asset_price_history
-- -----------------------------------------------------------------------------
-- Histórico diário de cotação por ativo, gravado no fechamento do pregão.
-- Hoje a tabela só escreve: a leitura nunca foi ligada (ver PENDENCIAS.txt,
-- seção 11).
CREATE TABLE asset_price_history (
    id             BIGSERIAL     PRIMARY KEY,
    asset_id       BIGINT        NOT NULL,
    reference_date DATE          NOT NULL,
    open_price     NUMERIC(18,8),
    close_price    NUMERIC(18,8),
    high_price     NUMERIC(18,8),
    low_price      NUMERIC(18,8),
    volume         BIGINT,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_price_history_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT uk_price_history_day   UNIQUE (asset_id, reference_date)
);

CREATE INDEX idx_price_history_asset_date ON asset_price_history (asset_id, reference_date);


-- -----------------------------------------------------------------------------
-- strategies
-- -----------------------------------------------------------------------------
-- Percentual ideal por tipo de ativo, base do rebalanceamento.
-- Um registro por usuário e tipo.
CREATE TABLE strategies (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL,
    asset_type_id       BIGINT       NOT NULL,
    target_percentage   NUMERIC(5,2) NOT NULL,
    include_reservation BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_strategies_user  FOREIGN KEY (user_id)       REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_strategies_type  FOREIGN KEY (asset_type_id) REFERENCES asset_types (id),
    CONSTRAINT uk_strategies_scope UNIQUE (user_id, asset_type_id),
    CONSTRAINT ck_strategies_percentage CHECK (target_percentage >= 0 AND target_percentage <= 100)
);

CREATE INDEX idx_strategies_user ON strategies (user_id);


-- -----------------------------------------------------------------------------
-- ceiling_prices
-- -----------------------------------------------------------------------------
-- Preço teto definido pelo usuário para um ativo.
CREATE TABLE ceiling_prices (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL,
    asset_id      BIGINT        NOT NULL,
    max_price     NUMERIC(18,8) NOT NULL,
    alert_enabled BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ceiling_prices_user  FOREIGN KEY (user_id)  REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ceiling_prices_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT uk_ceiling_prices_scope UNIQUE (user_id, asset_id)
);

CREATE INDEX idx_ceiling_prices_user ON ceiling_prices (user_id);


-- -----------------------------------------------------------------------------
-- goals
-- -----------------------------------------------------------------------------
-- Metas de investimento do usuário.
CREATE TABLE goals (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID          NOT NULL,
    name           VARCHAR(255)  NOT NULL,
    target_amount  NUMERIC(18,2) NOT NULL,
    current_amount NUMERIC(18,2),
    target_date    DATE,
    status         VARCHAR(20)   NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_goals_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_goals_user ON goals (user_id);
