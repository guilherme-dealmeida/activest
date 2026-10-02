-- Datas de pagamento levantadas (PLANO_CARGA_INICIAL.md, secao 11 - D14).
--
-- O historico longo da B3 nao traz a data de pagamento dos proventos de acao; os relatorios da CVM
-- (payment_date_source = 'CVM') so existem de 2022 em diante. O levantamento manual de 2010-2026
-- (data/levantamento/datas-pagamento.json, aplicado pela adm-api) completa o resto com
-- payment_date_source = 'LEVANTAMENTO'. Como a CVM, cede a data da propria fonte do evento
-- (CashEventWriter). Sem dados a migrar: so a regra.
ALTER TABLE asset_cash_events DROP CONSTRAINT ck_asset_cash_events_payment_date_source;
ALTER TABLE asset_cash_events ADD CONSTRAINT ck_asset_cash_events_payment_date_source CHECK (
    payment_date_source IS NULL
    OR (payment_date_source IN ('CVM', 'LEVANTAMENTO') AND payment_date IS NOT NULL)
);
