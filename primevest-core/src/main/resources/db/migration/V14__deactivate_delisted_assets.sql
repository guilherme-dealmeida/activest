-- Ativos que sairam de negociacao ficam inativos (PLANO_BRAPI.md, B4 e B19).
--
-- Criterio de deslistado (secao 2.1 do plano): nao aparece na lista oficial da
-- BRAPI E a cotacao devolve 404. Em 23/09/2026 isso deu 6 fracionarios:
-- AHEB3F, ESTR3F, FIGE4F, MSPA4F, PEAB3F e RPAD6F. Os 27 outros ausentes da
-- lista tem cotacao do dia (papel sem negocio, nao deslistado) e ficam ativos.
--
-- FSPE11F, FSRF11F e PSVM11F estavam no catalogo de acoes, mas sao fundos
-- fiscais antigos (FISET) e a Porto Sudeste: saem da busca pelo mesmo caminho.
--
-- Protecao: ativo com posicao aberta (cotas > 0 somando as transacoes) NAO e
-- desligado — a migration pula e registra um NOTICE. Inativo some da busca do
-- autocomplete, mas a posicao que ja existe continua na carteira.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT a.id, a.ticker FROM assets a WHERE a.ticker IN ('AHEB3F', 'ESTR3F', 'FIGE4F', 'MSPA4F', 'PEAB3F', 'RPAD6F', 'FSPE11F', 'FSRF11F', 'PSVM11F') LOOP
        IF EXISTS (
            SELECT 1
            FROM portfolio_assets pa
            JOIN transactions tx ON tx.portfolio_asset_id = pa.id
            WHERE pa.asset_id = r.id
            GROUP BY pa.id
            HAVING SUM(CASE WHEN tx.type = 'BUY' THEN COALESCE(tx.shares, 0) ELSE -COALESCE(tx.shares, 0) END) > 0
        ) THEN
            RAISE NOTICE 'V14: % tem posicao aberta e continua ativo', r.ticker;
        ELSE
            UPDATE assets SET active = FALSE WHERE id = r.id;
        END IF;
    END LOOP;
END
$$;
