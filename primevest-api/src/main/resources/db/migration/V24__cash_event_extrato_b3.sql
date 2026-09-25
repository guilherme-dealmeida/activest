-- Proventos lancados a partir do extrato da Area do Investidor da B3.
--
-- Enquanto nao houver fonte fixa para o historico de FII alem de 12 meses (o
-- FNET e instavel), as lacunas do catalogo sao preenchidas pelo extrato de
-- proventos que o investidor baixa da B3: POST /api/v1/admin/proventos/extrato.
--
-- source = 'EXTRATO_B3'   evento criado a partir do extrato. O extrato traz o
--                         pagamento, nao a data-com: a data-com e DEDUZIDA do
--                         padrao do fundo (ex.: ultimo pregao do mes anterior
--                         ao pagamento) e conferida contra as cotas do extrato.
--
-- payment_date_source     de onde veio a data de pagamento, quando NAO veio da
--                         propria fonte do evento:
--   NULL        veio da fonte do evento (B3, FNET) — oficial
--   EXTRATO_B3  veio do extrato do investidor
--   DEDUZIDA    deduzida do padrao de pagamento da empresa (ex.: a 2a parcela
--               de JCP da Cemig em 30/12), ainda sem confirmacao
-- Quando uma fonte oficial traz a data, ela substitui a do extrato ou a
-- deduzida e a coluna volta a NULL (ProventosSyncService.saveCashEvent).
ALTER TABLE asset_cash_events DROP CONSTRAINT ck_asset_cash_events_source;
ALTER TABLE asset_cash_events ADD CONSTRAINT ck_asset_cash_events_source
    CHECK (source IN ('B3', 'B3_HISTORICO', 'FNET', 'EXTRATO_B3'));

ALTER TABLE asset_cash_events ADD COLUMN payment_date_source VARCHAR(20);
ALTER TABLE asset_cash_events ADD CONSTRAINT ck_asset_cash_events_payment_date_source CHECK (
    payment_date_source IS NULL
    OR (payment_date_source IN ('EXTRATO_B3', 'DEDUZIDA') AND payment_date IS NOT NULL)
);
