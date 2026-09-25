-- Ativos que existem na B3 e faltavam no catalogo (PLANO_BRAPI.md, B7 e B8).
--
-- Fonte: lista de tickers da BRAPI, retrato de 24/09/2026. BRAX, ESUD11 e
-- ABCB10L ficam de fora de proposito (nao sao acao: cadastro sujo, FIP e ticker
-- temporario — secao 2.2 do plano).
--
-- ON CONFLICT DO NOTHING, como nos seeds: ativo que ja exista pode ter posicao.

-- 14 acoes. A categoria e a do papel irmao do mesmo emissor, porque a B3
-- classifica por EMISSOR (CEEB5 herda a do CEEB3). O irmao e resolvido por
-- ticker, e nao por literal de categoria, para as duas linhas nunca divergirem.
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id)
SELECT v.ticker::varchar, v.name::varchar, t.id, s.asset_category_id
FROM (VALUES
    ('CCTY3', 'BELORA RDVC CITY DESENVOLVIMENTO  IMOBILIÁRIO S.A.', 'CCTY3F'),
    ('CEEB5', 'CIA ELETRICIDADE EST. DA BAHIA - COELBA', 'CEEB3'),
    ('CTKA3', 'KARSTEN S.A.', 'CTKA4'),
    ('CTKA3F', 'KARSTEN S.A.', 'CTKA4'),
    ('EQPA7', 'EQUATORIAL PARA DISTRIBUIDORA DE ENERGIA S.A.', 'EQPA3'),
    ('GSHP3', 'GENERAL SHOPPING E OUTLETS DO BRASIL S.A.', 'GSHP3F'),
    ('HBTS5', 'CIA HABITASUL DE PARTICIPACOES', 'HBTS5F'),
    ('MGEL3F', 'MANGELS INDUSTRIAL S.A.', 'MGEL4'),
    ('MRSA5B', 'MRS LOGISTICA S.A.', 'MRSA3B'),
    ('MRSA6B', 'MRS LOGISTICA S.A.', 'MRSA3B'),
    ('MTSA3F', 'METISA METALURGICA TIMBOENSE S.A.', 'MTSA4'),
    ('RPAD5', 'ALFA HOLDINGS S.A.', 'RPAD3'),
    ('RPAD6', 'ALFA HOLDINGS S.A.', 'RPAD3'),
    ('WLMM3', 'WLM PART. E COMÉRCIO DE MÁQUINAS E VEÍCULOS S.A.', 'WLMM4')
) AS v(ticker, name, sibling)
JOIN asset_types t ON t.name = 'ACAO'
LEFT JOIN assets s ON s.ticker = v.sibling::varchar
ON CONFLICT (ticker) DO NOTHING;

-- 9 FIIs, classificados pelo segmento publicado (FIIs.pro, 24/09/2026) no mapa
-- de categorias do V3. JASC11 e publicado como "Renda Urbana", que nao existe
-- no V3: entra como Varejo, o que o fundo de fato tem.
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id)
SELECT v.ticker::varchar, v.name::varchar, t.id, c.id
FROM (VALUES
    ('APXU11', 'Apex Urb', 'HIBRIDO', 'Híbrido'),
    ('FPNG11', 'Pedra Negra Renda Imobiliária', 'TIJOLO', 'Lajes Corporativas'),
    ('IMMB11', 'Immobinvest', 'TIJOLO', 'Imóveis Residenciais'),
    ('JASC11', 'Jasc Renda Varejo Essencial', 'TIJOLO', 'Varejo'),
    ('LSOI11', 'LCP Special Opportunities I', 'HIBRIDO', 'Híbrido'),
    ('LSOP11', 'CP Special Opportunities III', 'TIJOLO', 'Imóveis Residenciais'),
    ('RBFY11', 'Rio Bravo For You', 'TIJOLO', 'Imóveis Residenciais'),
    ('RBTS11', 'RB Capital TFO Situs', 'TIJOLO', 'Desenvolvimento'),
    ('ROOF11', 'Rooftop I', 'TIJOLO', 'Desenvolvimento')
) AS v(ticker, name, cat_name, cat_segment)
JOIN asset_types t ON t.name = 'FII'
LEFT JOIN asset_categories c
       ON c.asset_type_id = t.id
      AND c.name    IS NOT DISTINCT FROM v.cat_name::varchar
      AND c.segment IS NOT DISTINCT FROM v.cat_segment::varchar
ON CONFLICT (ticker) DO NOTHING;

-- INHF11 estava no V4 como acao ("INTER HOLDING FINANCEIRA S.A.", OUTROS), mas e
-- o FII Inter Hedge. A linha e corrigida, nao recriada: se houver posicao nela,
-- ela passa de Acoes para FIIs na alocacao (decisao B8).
UPDATE assets a
SET name = 'Inter Hedge',
    asset_type_id = t.id,
    asset_category_id = c.id
FROM asset_types t
JOIN asset_categories c ON c.asset_type_id = t.id AND c.name = 'HIBRIDO' AND c.segment = 'Híbrido'
WHERE t.name = 'FII'
  AND a.ticker = 'INHF11';
