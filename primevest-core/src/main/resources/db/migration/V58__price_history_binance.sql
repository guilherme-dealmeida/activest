-- Historico de cripto pela Binance (docs/planos/PLANO_RENTABILIDADE.md, 3.2; D2 de 17/10/2026).
--
-- A CoinGecko gratuita so devolve os ultimos 365 dias; o BTC/BRL da Binance (publico, klines diario) vai ate
-- 13/10/2020. A Binance so preenche datas que faltam: linha COINGECKO gravada nao e sobrescrita.

ALTER TABLE asset_price_history DROP CONSTRAINT ck_price_history_source;
ALTER TABLE asset_price_history ADD CONSTRAINT ck_price_history_source
    CHECK (source IN ('BRAPI', 'COINGECKO', 'B3_COTAHIST', 'TESOURO', 'BINANCE'));
