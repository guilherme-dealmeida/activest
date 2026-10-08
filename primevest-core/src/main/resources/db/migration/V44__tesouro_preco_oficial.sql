-- Tesouro Direto pelo preco oficial (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, R1; D1-D3 de 05/10/2026).
--
-- O Tesouro publica todo dia util o PU de cada titulo (Tesouro Transparente, PrecoTaxaTesouroDireto.csv). O PU de
-- venda (resgate antecipado, D2) passa a ser a cotacao do titulo: vai para asset_price_history (source TESOURO) e o
-- ultimo para assets.current_price — e a valorizacao do Tesouro passa a sair do AppreciationUpdater, como a das acoes
-- (cotas x PU - custo, por titulo). A taxa da nota continua a da compra.

ALTER TABLE asset_types DROP CONSTRAINT ck_asset_types_price_source;
ALTER TABLE asset_types ADD CONSTRAINT ck_asset_types_price_source
    CHECK (price_source IN ('BRAPI', 'COINGECKO', 'TESOURO', 'MANUAL'));
UPDATE asset_types SET price_source = 'TESOURO' WHERE name = 'TESOURO_DIRETO';

ALTER TABLE asset_price_history DROP CONSTRAINT ck_price_history_source;
ALTER TABLE asset_price_history ADD CONSTRAINT ck_price_history_source
    CHECK (source IN ('BRAPI', 'COINGECKO', 'B3_COTAHIST', 'TESOURO'));
