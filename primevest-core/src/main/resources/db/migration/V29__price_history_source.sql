-- Origem de cada pregao em asset_price_history (PLANO_EMPRESAS_BALANCOS.md, F2, migration M2).
--
-- A serie historica da B3 (COTAHIST) passa a carregar o historico diario desde
-- 2010; a BRAPI continua gravando o fechamento do dia e a CoinGecko o de cripto.
-- As fontes convivem na mesma tabela (conferido em 27/09/2026: fechamento da
-- BRAPI identico ao da B3). Regra: dia gravado pela B3 nao e sobrescrito pela
-- BRAPI — a serie oficial vence.
--
-- Linhas existentes: BRAPI (seeds V17/V18 e fechamento), exceto as de cripto,
-- que vieram da CoinGecko.
ALTER TABLE asset_price_history ADD COLUMN source VARCHAR(15) NOT NULL DEFAULT 'BRAPI';

UPDATE asset_price_history h
SET source = 'COINGECKO'
FROM assets a
JOIN asset_types t ON t.id = a.asset_type_id
WHERE a.id = h.asset_id AND t.name = 'CRIPTO';

ALTER TABLE asset_price_history ADD CONSTRAINT ck_price_history_source
    CHECK (source IN ('BRAPI', 'COINGECKO', 'B3_COTAHIST'));
