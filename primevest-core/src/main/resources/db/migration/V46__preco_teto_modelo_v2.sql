-- Preço Teto pelo modelo PREÇO TETO V2 (docs/planos/PLANO_PRECO_TETO.md, 4.1 e 4.2 — 10/10/2026).
--
-- O preço teto deixa de ser digitado (D7): passa a ser calculado das entradas do usuário, todas gravadas e
-- obrigatórias (o formulário vem com os padrões do catálogo; ao editar voltam as gravadas). O resultado NÃO é
-- gravado: sai das entradas (CeilingPriceRules, app-api). Um cálculo por (usuário, ativo), sobrescrito a cada
-- "Salvar" (D9). Saem max_price e alert_enabled (este nunca foi usado — D10).
--
-- A tabela é recriada: estava vazia em 10/10/2026 (conferido). A trava abaixo impede rodar sobre dados.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM ceiling_prices) THEN
        RAISE EXCEPTION 'ceiling_prices tem linhas: a V46 recria a tabela e so roda vazia (PLANO_PRECO_TETO, 4.1)';
    END IF;
END $$;

DROP TABLE ceiling_prices;

CREATE TABLE ceiling_prices (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID          NOT NULL,
    asset_id              BIGINT        NOT NULL,
    -- N: quantidade de ações (padrão: sem tesouraria, ajustada por eventos; unit em units)
    shares                BIGINT        NOT NULL,
    -- percentuais em pontos (13,75 = 13,75%)
    roe_pct               NUMERIC(9,4)  NOT NULL,
    payout_pct            NUMERIC(9,4)  NOT NULL,
    discount_rate_pct     NUMERIC(9,4)  NOT NULL,
    perpetual_growth_pct  NUMERIC(9,4)  NOT NULL,
    safety_margin_pct     NUMERIC(9,4)  NOT NULL,
    -- preço usado na comparação do cálculo
    price                 NUMERIC(18,8) NOT NULL,
    -- por campo: {"origin": "PADRAO"|"INFORMADO", "ref": referência do padrão, "default": valor do padrão}
    inputs_origin         JSONB         NOT NULL,
    notes                 VARCHAR(500),
    calculated_at         TIMESTAMP     NOT NULL,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ceiling_prices_user  FOREIGN KEY (user_id)  REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ceiling_prices_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT uk_ceiling_prices_scope UNIQUE (user_id, asset_id),
    CONSTRAINT ck_ceiling_prices_shares CHECK (shares > 0),
    CONSTRAINT ck_ceiling_prices_price CHECK (price > 0),
    CONSTRAINT ck_ceiling_prices_margin CHECK (safety_margin_pct >= 0 AND safety_margin_pct < 100)
);

CREATE INDEX idx_ceiling_prices_user ON ceiling_prices (user_id);

-- Os quatro lucros do cálculo (T-3 … T), sempre 4 linhas por cálculo.
CREATE TABLE ceiling_price_quarters (
    ceiling_price_id  UUID          NOT NULL,
    position          SMALLINT      NOT NULL,
    period_end        DATE          NOT NULL,
    -- como foi digitado (pode ser negativo), com unidade, moeda e câmbio
    original_value    NUMERIC(24,2) NOT NULL,
    unit              VARCHAR(8)    NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    fx_rate           NUMERIC(18,8) NOT NULL,
    -- normalizado em reais: o que entra no cálculo
    value_brl         NUMERIC(24,2) NOT NULL,
    -- PADRAO: veio da CVM sem alteração; INFORMADO: digitado pelo usuário
    origin            VARCHAR(10)   NOT NULL,
    -- PADRAO: o documento ("CVM ITR 30/06/2026 v1"); INFORMADO: a nota do usuário
    source_ref        VARCHAR(300),
    CONSTRAINT pk_ceiling_price_quarters PRIMARY KEY (ceiling_price_id, position),
    CONSTRAINT fk_ceiling_price_quarters_cp FOREIGN KEY (ceiling_price_id) REFERENCES ceiling_prices (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_ceiling_price_quarters_position CHECK (position BETWEEN 1 AND 4),
    CONSTRAINT ck_ceiling_price_quarters_unit CHECK (unit IN ('UNIDADE', 'MIL', 'MILHAO', 'BILHAO')),
    CONSTRAINT ck_ceiling_price_quarters_currency CHECK (currency IN ('BRL', 'USD')),
    CONSTRAINT ck_ceiling_price_quarters_fx CHECK (fx_rate > 0),
    CONSTRAINT ck_ceiling_price_quarters_origin CHECK (origin IN ('PADRAO', 'INFORMADO'))
);
