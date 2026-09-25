-- Parcelas do mesmo provento (PLANO_BRAPI.md, F5).
--
-- A B3 publica o provento pago em parcelas como linhas iguais: mesmo tipo,
-- mesma data-com, mesmo valor por acao, so a data de pagamento muda. PETR4,
-- data-com 01/06/2026: JCP de R$ 0,35048636 pago em 20/08 E outro de R$
-- 0,35048636 pago em 21/09. A unicidade da V20 (ativo, tipo, data-com, valor)
-- tratava a segunda parcela como a mesma noticia repetida e ela sumia — o
-- acionista recebia metade.
--
-- seq numera as linhas iguais dentro da mesma resposta da fonte (1, 2...). A
-- mesma parcela vinda de outra fonte (o historico longo da B3 tambem lista as
-- duas) casa pelo mesmo numero, e a deduplicacao entre fontes continua valendo.
ALTER TABLE asset_cash_events ADD COLUMN seq SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE asset_cash_events DROP CONSTRAINT uk_asset_cash_events;
ALTER TABLE asset_cash_events ADD CONSTRAINT uk_asset_cash_events
    UNIQUE NULLS NOT DISTINCT (asset_id, type, data_com, value_per_share, seq);
