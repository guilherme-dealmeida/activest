-- Move o INHF11 de FII para ACAO.
--
-- A V19 o carregou no seed de fundos imobiliários com o nome
-- "INTER HOLDING FINANCEIRA S.A." — holding financeira, não fundo. Foi o único
-- dos 338 FIIs que veio com nome real em vez do próprio ticker, e também o
-- único que não aparece na consulta de FIIs do StatusInvest. Os dois sinais
-- apontam para a mesma coisa: entrou no seed errado, como o BRAX (V25).
--
-- A classificação vem da ITSA4, por ser o caso análogo já resolvido no
-- catálogo: holding de participações financeiras. Em vez de fixar o id da
-- categoria, que muda entre bancos recriados, a migration lê da própria ITSA4 —
-- assim o INHF11 fica com exatamente o que a V23 atribuiu a ela.
--
-- O bloco falha alto se a referência não existir. Um UPDATE ... FROM com
-- subconsulta vazia atualizaria zero linhas em silêncio, e o INHF11 continuaria
-- como FII sem ninguém perceber.
DO $$
DECLARE
    v_acao_id BIGINT;
    v_cat_id  BIGINT;
    v_sector  VARCHAR(255);
    v_linhas  INT;
BEGIN
    SELECT id INTO v_acao_id FROM asset_types WHERE name = 'ACAO';
    IF v_acao_id IS NULL THEN
        RAISE EXCEPTION 'Tipo de ativo ACAO não encontrado';
    END IF;

    SELECT asset_category_id, sector INTO v_cat_id, v_sector
    FROM assets WHERE ticker = 'ITSA4';
    IF NOT FOUND THEN
        RAISE EXCEPTION 'ITSA4 não existe no catálogo — sem referência para classificar o INHF11';
    END IF;
    IF v_cat_id IS NULL THEN
        RAISE EXCEPTION 'ITSA4 está sem categoria; a V23 precisa rodar antes desta migration';
    END IF;

    -- sector acompanha a categoria: o INHF11 passa a ser lido pelas mesmas
    -- regras das outras ações, inclusive pelo scheduler de cotações, que agora
    -- vai buscá-lo na BrAPI (price_source do tipo ACAO) em vez de ignorá-lo.
    UPDATE assets
    SET asset_type_id     = v_acao_id,
        asset_category_id = v_cat_id,
        sector            = v_sector
    WHERE ticker = 'INHF11';

    GET DIAGNOSTICS v_linhas = ROW_COUNT;
    RAISE NOTICE 'INHF11: % linha(s) movida(s) para ACAO (categoria %, setor %)',
                 v_linhas, v_cat_id, v_sector;
END $$;
