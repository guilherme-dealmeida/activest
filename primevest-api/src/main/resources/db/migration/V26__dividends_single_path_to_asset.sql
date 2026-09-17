-- Proventos chegam ao ativo por um caminho só, e o total por posição deixa de
-- ser gravado.
--
-- 1. dividends.asset_id sai. O ativo já é alcançado pela posição
--    (dividends -> portfolio_assets -> assets). Dois caminhos para o mesmo
--    ativo são duas fontes que podem divergir: nada impedia um provento de
--    PETR4 apontar para uma posição de VALE3.
--
--    Com isso portfolio_asset_id vira obrigatório: é por ele que o provento
--    chega ao ativo e ao dono. O antigo ON DELETE SET NULL produzia provento
--    sem ativo e sem dono, invisível até para quem o recebeu; vira CASCADE,
--    como em transactions.
--
-- 2. portfolio_assets.total_dividends sai. O total de uma posição é a soma de
--    dividends.total_amount dela, calculada na leitura. Gravado, só ficava
--    certo enquanto todo caminho que cria ou apaga provento lembrasse de
--    atualizá-lo.

-- Guarda: provento sem posição não tem para onde ir depois desta migration.
-- Apagá-lo em silêncio seria perder dado; parar é mais barato.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM dividends WHERE portfolio_asset_id IS NULL) THEN
        RAISE EXCEPTION 'Existem proventos sem portfolio_asset_id. Associe-os a uma posição ou apague-os antes de aplicar V26.';
    END IF;
END $$;

-- Guarda: provento cuja posição é de outro ativo. Com asset_id fora, o ativo
-- passaria a ser o da posição, e o provento mudaria de ativo sem ninguém ver.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM dividends d
        JOIN portfolio_assets pa ON pa.id = d.portfolio_asset_id
        WHERE pa.asset_id <> d.asset_id
    ) THEN
        RAISE EXCEPTION 'Existem proventos cujo asset_id difere do ativo da posição. Corrija-os antes de aplicar V26.';
    END IF;
END $$;

ALTER TABLE dividends DROP CONSTRAINT fk_dividends_portfolio_asset;
ALTER TABLE dividends ALTER COLUMN portfolio_asset_id SET NOT NULL;
ALTER TABLE dividends
    ADD CONSTRAINT fk_dividends_portfolio_asset
    FOREIGN KEY (portfolio_asset_id) REFERENCES portfolio_assets (id) ON DELETE CASCADE;

-- Derruba junto a FK fk_dividends_asset e o índice idx_dividends_asset.
ALTER TABLE dividends DROP COLUMN asset_id;

ALTER TABLE portfolio_assets DROP COLUMN total_dividends;
