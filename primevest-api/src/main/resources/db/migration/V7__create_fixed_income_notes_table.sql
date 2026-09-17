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
-- asset_terms; a nota os copia no momento do lançamento. A cópia é
-- intencional: o que vale para o cálculo é o que foi contratado, e uma
-- correção futura no catálogo não deve reescrever uma posição já registrada.
--
-- UUID como nas outras tabelas de dados do usuário. BIGSERIAL fica para o
-- catálogo.
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
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
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
