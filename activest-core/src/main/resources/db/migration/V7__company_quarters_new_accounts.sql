-- Contas novas da serie trimestral (docs/planos/PLANO_TELA_ATIVOS.md, 2.5 e etapa A1, 23/10/2026): base dos
-- indicadores novos de acoes da tela de Ativos (Margem Bruta, EBITDA, ROIC, Liquidez Corrente, capital de giro).
--
-- Fluxos (trimestre isolado _q e 12 meses _ttm), lidos da CVM por FinancialMetricRules:
--   gross_profit               resultado bruto (DRE 3.03)                         so layout comercial
--   depreciation_amortization  depreciacao, amortizacao e exaustao (DVA 7.04.01)  so layout comercial
--   pretax_income              resultado antes dos tributos sobre o lucro (DRE)  todos os layouts
--   income_tax                 IR e CSLL sobre o lucro (DRE), negativo quando e despesa   todos os layouts
-- Posicao na data do balanco (so layout comercial):
--   current_assets             ativo circulante (BPA 1.01)
--   current_liabilities        passivo circulante (BPP 2.01)
--
-- Nulas ao nascer: a tabela e derivada e refeita inteira pela carga de balancos (CompanyQuarterService), entao nao ha
-- valor default a informar (a regra 8.1 do docs/REGRAS_CLAUDE.md vale para campo obrigatorio de tabela com dado
-- proprio). O reprocessamento (POST /api/v1/admin/cvm/import?force=true) preenche; num ambiente novo, o bootstrap.
ALTER TABLE company_quarters
    ADD COLUMN gross_profit_q                NUMERIC(24,2),
    ADD COLUMN gross_profit_ttm              NUMERIC(24,2),
    ADD COLUMN depreciation_amortization_q   NUMERIC(24,2),
    ADD COLUMN depreciation_amortization_ttm NUMERIC(24,2),
    ADD COLUMN pretax_income_q               NUMERIC(24,2),
    ADD COLUMN pretax_income_ttm             NUMERIC(24,2),
    ADD COLUMN income_tax_q                  NUMERIC(24,2),
    ADD COLUMN income_tax_ttm                NUMERIC(24,2),
    ADD COLUMN current_assets                NUMERIC(24,2),
    ADD COLUMN current_liabilities           NUMERIC(24,2);
