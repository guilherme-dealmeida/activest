-- Catalogo sem o extrato do investidor (PLANO_CARGA_INICIAL.md, regra C e D13, 29/09/2026).
--
-- A V24 deixou o extrato de proventos da Area do Investidor gravar no catalogo: eventos com
-- source = 'EXTRATO_B3' e datas de pagamento com payment_date_source = 'EXTRATO_B3' ou 'DEDUZIDA'.
-- Foi um erro: evento do catalogo vem de integracao (B3, FNET, CVM); o que so o extrato de um
-- usuario tem e dado dele. Num banco novo nada disto existe; aqui o banco que ja tinha e acertado.

-- 1. FII: os avisos do FNET cobrem esses meses (estavam em data/fnet; o extrato so foi gravado
--    antes). Passam a FNET sem o id do aviso e sem a data do extrato: a rotina de proventos rele o
--    aviso, guarda o id e a data de pagamento dele (CashEventWriter).
UPDATE asset_cash_events e
SET source = 'FNET', payment_date = NULL, payment_date_source = NULL
FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
WHERE e.asset_id = a.id AND e.source = 'EXTRATO_B3' AND t.name = 'FII';

-- 2. O resto (ex.: PETR4, atualizacao pela Selic que a B3 nao publica): sai do catalogo. O
--    provento de quem recebeu continua na carteira dele, como lancamento proprio (MANUAL, sem
--    evento): o recalculo nao o apaga e ele nunca volta ao catalogo.
UPDATE dividends d
SET origin = 'MANUAL', cash_event_id = NULL
FROM asset_cash_events e
WHERE d.cash_event_id = e.id AND e.source = 'EXTRATO_B3';

DELETE FROM asset_cash_events WHERE source = 'EXTRATO_B3';

-- 3. Datas de pagamento do extrato ou deduzidas: saem. A data vem da B3 (12 meses, acumulados em
--    data/b3/proventos-12m) ou dos relatorios de proventos da CVM (payment_date_source = 'CVM').
UPDATE asset_cash_events
SET payment_date = NULL, payment_date_source = NULL
WHERE payment_date_source IN ('EXTRATO_B3', 'DEDUZIDA');

ALTER TABLE asset_cash_events DROP CONSTRAINT ck_asset_cash_events_source;
ALTER TABLE asset_cash_events ADD CONSTRAINT ck_asset_cash_events_source
    CHECK (source IN ('B3', 'B3_HISTORICO', 'FNET'));

ALTER TABLE asset_cash_events DROP CONSTRAINT ck_asset_cash_events_payment_date_source;
ALTER TABLE asset_cash_events ADD CONSTRAINT ck_asset_cash_events_payment_date_source CHECK (
    payment_date_source IS NULL
    OR (payment_date_source = 'CVM' AND payment_date IS NOT NULL)
);
