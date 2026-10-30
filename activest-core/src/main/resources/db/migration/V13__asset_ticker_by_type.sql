-- Ticker único por tipo de ativo (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, D3, I4, P2 — etapa F1, 29/10/2026).
--
-- Até aqui o ticker era único no catálogo inteiro (uk_assets_ticker). Com os ativos do exterior isso deixa de valer:
-- as criptos têm ticker só de letras e várias coincidem com papéis americanos (META = MetaDAO e Meta Platforms, CVX =
-- Convex e Chevron, LTC = Litecoin e LTC Properties). A unicidade passa a ser (ticker, tipo) entre os papéis vivos; o
-- deslistado mantém o ticker que tinha e se distingue pela data de saída (P2: na mesma bolsa um ticker pode ser
-- reaproveitado por outra empresa depois que o primeiro papel saiu).

-- 1. Metas que apontam para um ativo passam a guardar o id (I4). A conversão usa o ticker, que até esta migration é
--    único no catálogo; o ticker continua em params como rótulo (a API o preenche pelo id a cada gravação).
UPDATE goals g
   SET params = g.params || jsonb_build_object('assetId', a.id)
  FROM assets a
 WHERE g.params ? 'ticker'
   AND a.ticker = g.params ->> 'ticker';

-- Escopo "por ativo": os valores deixam de ser tickers e passam a ser ids (em texto, como os demais valores de escopo).
UPDATE goals g
   SET scope = jsonb_set(g.scope, '{values}',
                         (SELECT COALESCE(jsonb_agg(to_jsonb(a.id::text) ORDER BY v.ord), '[]'::jsonb)
                            FROM jsonb_array_elements_text(g.scope -> 'values') WITH ORDINALITY AS v(t, ord)
                            JOIN assets a ON a.ticker = v.t OR a.ticker = upper(v.t)))
 WHERE g.scope ->> 'kind' = 'ASSET';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM goals WHERE params ? 'ticker' AND NOT params ? 'assetId') THEN
        RAISE EXCEPTION 'V13: meta com ticker sem ativo correspondente no catálogo';
    END IF;
END $$;

-- 2. Data de saída da bolsa (D11; preenchida a partir da F2). Só o papel inativo tem data de saída.
ALTER TABLE assets
    ADD COLUMN delisted_on DATE,
    ADD CONSTRAINT ck_assets_delisted_inactive CHECK (delisted_on IS NULL OR active = false);

COMMENT ON COLUMN assets.delisted_on IS 'Data de saída da bolsa (D11). Nula no papel listado; faz parte da unicidade (P2).';

-- 3. Unicidade: (ticker, tipo, data de saída), com NULLS NOT DISTINCT — entre os que não saíram da bolsa (data nula),
--    ticker + tipo é único; dois deslistados com o mesmo ticker se distinguem pela data. O índice começa pelo ticker e
--    atende as buscas por ticker (o idx_assets_ticker em cópia saiu na V12).
ALTER TABLE assets DROP CONSTRAINT uk_assets_ticker;
ALTER TABLE assets
    ADD CONSTRAINT uk_assets_ticker_type UNIQUE NULLS NOT DISTINCT (ticker, asset_type_id, delisted_on);
