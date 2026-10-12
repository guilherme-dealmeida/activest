-- Payout pelo fluxo de caixa (docs/planos/PLANO_PRECO_TETO.md, 1.5 e D4 — 10/10/2026).
--
-- A DVA de muitas companhias nao traz dividendos (Vale: tudo em "Lucros Retidos"; 167 de 357 no DFP 2025 com
-- dividendos zerados), e o payout calculado por ela dava 0%. A base passa a ser o que foi pago aos acionistas da
-- controladora na DFC (atividades de financiamento): metrica DIVIDENDOS_PAGOS em cvm_statement_values (sem mudanca
-- de estrutura) e as duas colunas abaixo na serie trimestral.
--
-- company_quarters e derivada e recalculada inteira (CompanyQuarterService): as colunas nascem nulas e sao
-- preenchidas no reprocessamento dos balancos (POST /api/v1/admin/cvm/import?force=true, sem download).
-- dividends_declared_* (DVA) continuam, so como demonstrativo.

ALTER TABLE company_quarters
    ADD COLUMN dividends_paid_q   NUMERIC(24, 2),
    ADD COLUMN dividends_paid_ttm NUMERIC(24, 2);

COMMENT ON COLUMN company_quarters.dividends_paid_q IS
    'Dividendos + JCP pagos aos acionistas da controladora no trimestre (DFC), positivo. Base do payout.';
COMMENT ON COLUMN company_quarters.dividends_paid_ttm IS
    'Dividendos + JCP pagos aos acionistas da controladora em 12 meses (DFC), positivo. Base do payout.';
