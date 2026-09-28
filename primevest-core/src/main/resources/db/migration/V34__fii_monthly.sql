-- Informe mensal de FII da CVM (F7 do PLANO_EMPRESAS_BALANCOS.md): um mes por fundo, cru, 2016 em diante.
-- Fonte: inf_mensal_fii_{complemento,ativo_passivo}_{ano}.csv (dados abertos da CVM), gravado pela adm-api
-- (FiiMonthlyImportService). Base dos indicadores de FII (5.2 do plano): P/VP, VP por cota, cotistas,
-- imoveis x papel, alavancagem.
--
-- reference_month: primeiro dia do mes (Data_Referencia da CVM). version: a maior versao recebida
-- (reapresentacao substitui). Valores numericos sem precisao fixa: o informe e digitado pelo administrador e
-- traz extremos (DY de 8,7e7 em 2022) que nao podem derrubar a carga.
-- Taxas (*_month) sao fracao do mes, como a CVM publica: 0.0068 = 0,68%.
--
-- Carteira (ativo_passivo), em R$:
--   liquidity_total        Total_Necessidades_Liquidez (caixa, titulos publicos/privados, fundos de renda fixa)
--   total_invested         Total_Investido (sem a liquidez)
--   real_estate_total      Direitos_Bens_Imoveis (terrenos, imoveis para renda e venda, outros direitos reais)
--   real_estate_companies  acoes + cotas de sociedades de atividades de FII (imoveis via SPE)
--   receivables_total      CRI + CRI/CRA + letras hipotecarias + LCI + LCI/LCA + LIG (o "papel")
--   fii_shares_total       cotas de outros FIIs
--   income_to_distribute   Rendimentos_Distribuir; / shares_issued = rendimento estimado (so indicador,
--                          nunca vira asset_cash_events — 1.2 do plano)

CREATE TABLE fii_monthly (
    issuer_id               BIGINT          NOT NULL,
    reference_month         DATE            NOT NULL,
    version                 INTEGER         NOT NULL,
    net_assets              NUMERIC,
    total_assets            NUMERIC,
    shares_issued           NUMERIC,
    nav_per_share           NUMERIC,
    shareholders            INTEGER,
    admin_fee_month         NUMERIC,
    dividend_yield_month    NUMERIC,
    amortization_month      NUMERIC,
    liquidity_total         NUMERIC,
    total_invested          NUMERIC,
    real_estate_total       NUMERIC,
    real_estate_companies   NUMERIC,
    receivables_total       NUMERIC,
    fii_shares_total        NUMERIC,
    liabilities_total       NUMERIC,
    income_to_distribute    NUMERIC,
    imported_at             TIMESTAMP       NOT NULL DEFAULT now(),
    CONSTRAINT pk_fii_monthly PRIMARY KEY (issuer_id, reference_month),
    CONSTRAINT fk_fii_monthly_issuer FOREIGN KEY (issuer_id) REFERENCES issuers (id),
    CONSTRAINT ck_fii_monthly_first_day CHECK (EXTRACT(DAY FROM reference_month) = 1)
);

CREATE INDEX ix_fii_monthly_month ON fii_monthly (reference_month);
