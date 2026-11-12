-- Curva de juros do Tesouro (docs/planos/PLANO_JUROS_FUTUROS.md, etapa J1).
--
-- Cada prazo fixo da curva vira um índice diário em % a.a. (unidade PCT_ANO, como a meta Selic: é uma taxa, não um
-- rendimento, e não acumula). A série é calculada pela adm-api a partir do arquivo de preços e taxas do Tesouro
-- Direto que a rotina TREASURY_PRICES já guarda em carga/data/tesouro (fonte TESOURO, sem download próprio).
-- source_ref = "<curva>:<anos>": PRE (Tesouro Prefixado, com e sem juros semestrais) e REAL (Tesouro IPCA+, com e sem
-- juros semestrais). Prazos decididos em 11/11/2026 (D3 revisada por I2): prefixada 1, 2, 3, 5 e 8 anos; real 4, 5,
-- 10 e 20 anos.

ALTER TABLE market_indices DROP CONSTRAINT ck_market_indices_source;
ALTER TABLE market_indices ADD CONSTRAINT ck_market_indices_source
    CHECK ((source IN ('BCB_SGS', 'B3', 'TESOURO')));

INSERT INTO market_indices (id, code, name, periodicity, unit, source, source_ref, history_from, active) VALUES
    (11, 'JUROS_PRE_1A', 'Juros prefixados (Tesouro) — 1 ano', 'DIARIA', 'PCT_ANO', 'TESOURO', 'PRE:1', '2010-01-01', true),
    (12, 'JUROS_PRE_2A', 'Juros prefixados (Tesouro) — 2 anos', 'DIARIA', 'PCT_ANO', 'TESOURO', 'PRE:2', '2010-01-01', true),
    (13, 'JUROS_PRE_3A', 'Juros prefixados (Tesouro) — 3 anos', 'DIARIA', 'PCT_ANO', 'TESOURO', 'PRE:3', '2010-01-01', true),
    (14, 'JUROS_PRE_5A', 'Juros prefixados (Tesouro) — 5 anos', 'DIARIA', 'PCT_ANO', 'TESOURO', 'PRE:5', '2010-01-01', true),
    (15, 'JUROS_PRE_8A', 'Juros prefixados (Tesouro) — 8 anos', 'DIARIA', 'PCT_ANO', 'TESOURO', 'PRE:8', '2010-01-01', true),
    (16, 'JUROS_REAL_4A', 'Juros reais (Tesouro IPCA+) — 4 anos', 'DIARIA', 'PCT_ANO', 'TESOURO', 'REAL:4', '2010-01-01', true),
    (17, 'JUROS_REAL_5A', 'Juros reais (Tesouro IPCA+) — 5 anos', 'DIARIA', 'PCT_ANO', 'TESOURO', 'REAL:5', '2010-01-01', true),
    (18, 'JUROS_REAL_10A', 'Juros reais (Tesouro IPCA+) — 10 anos', 'DIARIA', 'PCT_ANO', 'TESOURO', 'REAL:10', '2010-01-01', true),
    (19, 'JUROS_REAL_20A', 'Juros reais (Tesouro IPCA+) — 20 anos', 'DIARIA', 'PCT_ANO', 'TESOURO', 'REAL:20', '2010-01-01', true);
SELECT setval('market_indices_id_seq', 19);

UPDATE task_types
   SET description = 'Índices de mercado (CDI, Selic, IPCA, IGP-M, INPC, TR, meta Selic, PTAX, Ibovespa, IFIX) do Banco Central e da B3, e a curva de juros do Tesouro (prefixada e real, por prazo) calculada do arquivo do Tesouro Direto. Sem período: rodada incremental com janela de revisão.'
 WHERE type = 'MARKET_INDICES';
