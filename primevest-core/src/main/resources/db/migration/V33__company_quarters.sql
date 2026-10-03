-- Serie trimestral das companhias (F6 do PLANO_EMPRESAS_BALANCOS.md): derivada de cvm_statement_values e
-- share_counts, recalculada inteira ao fim de cada carga de balancos (CompanyQuarterService). Nao ha dado
-- proprio aqui: apagar e recalcular da o mesmo resultado.
--
-- Uma linha por documento (DFP ou ITR) da companhia, na data de referencia. Regras (5.1 do plano):
--   *_q   trimestre isolado: coluna de 3 meses do ITR quando existe; senao acumulado do exercicio menos o
--         acumulado do trimestre anterior (mesmo inicio de exercicio) — e assim sai o 4T (DFP - 9 meses) e
--         a DFC e a DVA, que so trazem acumulado. Faltando o documento anterior, nulo.
--   *_ttm 12 meses: valor anual do DFP na data do exercicio; senao soma dos 4 ultimos trimestres
--         consecutivos. Faltando um, nulo (nao estima).
--   posicao (PL, ativo, caixa, divida): o balanco do proprio documento.
-- dividends_declared: dividendos + JCP da DVA (remuneracao de capitais proprios), base do payout.
-- equity_parent: PL - participacao dos nao controladores.
-- shares_*: share_counts em unidades (quantidade * scale_factor); nulo quando a escala esta em REVISAR.
--   shares_outstanding = total - tesouraria (sem tesouraria informada, o total).

CREATE TABLE company_quarters (
    issuer_id                 BIGINT         NOT NULL,
    period_end                DATE           NOT NULL,
    fiscal_year_start         DATE,
    source_doc_id             BIGINT         NOT NULL,
    revenue_q                 NUMERIC(24, 2),
    ebit_q                    NUMERIC(24, 2),
    net_income_q              NUMERIC(24, 2),
    net_income_parent_q       NUMERIC(24, 2),
    fco_q                     NUMERIC(24, 2),
    dividends_declared_q      NUMERIC(24, 2),
    revenue_ttm               NUMERIC(24, 2),
    ebit_ttm                  NUMERIC(24, 2),
    net_income_ttm            NUMERIC(24, 2),
    net_income_parent_ttm     NUMERIC(24, 2),
    fco_ttm                   NUMERIC(24, 2),
    dividends_declared_ttm    NUMERIC(24, 2),
    equity                    NUMERIC(24, 2),
    equity_parent             NUMERIC(24, 2),
    total_assets              NUMERIC(24, 2),
    cash                      NUMERIC(24, 2),
    gross_debt                NUMERIC(24, 2),
    shares_total              BIGINT,
    shares_outstanding        BIGINT,
    computed_at               TIMESTAMP      NOT NULL DEFAULT now(),
    CONSTRAINT pk_company_quarters PRIMARY KEY (issuer_id, period_end),
    CONSTRAINT fk_company_quarters_issuer FOREIGN KEY (issuer_id) REFERENCES issuers (id),
    CONSTRAINT fk_company_quarters_document FOREIGN KEY (source_doc_id) REFERENCES cvm_documents (id) ON DELETE CASCADE
);

CREATE INDEX ix_company_quarters_period ON company_quarters (period_end);
