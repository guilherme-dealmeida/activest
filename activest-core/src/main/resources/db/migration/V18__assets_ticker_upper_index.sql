-- Índice da busca por ticker (docs/planos/PLANO_PERFORMANCE.md, P1 — etapa E1, 29/10/2026).
--
-- Com os fundos da CVM (V12) e os papéis dos EUA (V15–V17), `assets` passou de ~2,6 mil para ~100 mil linhas, e toda
-- consulta `WHERE upper(ticker) = ?` (indicadores, listagem de Ativos, Preço Teto, cotação da página do ativo,
-- Rentabilidade, deslistados dos EUA) virou leitura da tabela inteira: 18–22 ms cada. O `uk_assets_ticker_type`
-- (ticker, asset_type_id, delisted_on) não serve a `upper(ticker)`. Com este índice, ~0,3 ms.
CREATE INDEX idx_assets_ticker_upper ON assets (upper(ticker));
