-- Categoria sem segmento (F4 / M4 do PLANO_EMPRESAS_BALANCOS.md, decisao D11).
--
-- Ate aqui a categoria era o par (name, segment) dentro do tipo: BENS_INDUSTRIAIS existia em 17 linhas,
-- uma por segmento B3. O segundo nivel passou para o emissor:
--   ACAO   segmento da B3 em issuers.b3_segment (rotina de emissores, GetDetail)
--   FII    segmento de atuacao curado em issuers.segment (seed V30, que roda antes desta)
--   ETF, BDR, cripto   o segmento nao tinha informacao (setor da BRAPI ou 'Indefinido') e sai
--
-- Em cada (tipo, name) fica a linha de menor id; os ativos das outras sao reapontados para ela e as
-- outras sao apagadas (so assets referencia asset_categories). A descricao e a mesma em todas as linhas
-- de um mesmo (tipo, name). A alocacao nao muda: ela agrupa pela descricao.
--
-- Migrations antigas nao sao editadas (checksum do Flyway): num banco novo V3/V5/V15... rodam com a
-- coluna ainda existindo e esta migration consolida depois delas.

CREATE TEMP TABLE category_survivor ON COMMIT DROP AS
SELECT id, MIN(id) OVER (PARTITION BY asset_type_id, name) AS keep_id
FROM asset_categories;

UPDATE assets a
SET asset_category_id = s.keep_id
FROM category_survivor s
WHERE a.asset_category_id = s.id
  AND s.id <> s.keep_id;

DELETE FROM asset_categories c
USING category_survivor s
WHERE c.id = s.id
  AND s.id <> s.keep_id;

ALTER TABLE asset_categories DROP CONSTRAINT uk_asset_categories_scope;
ALTER TABLE asset_categories DROP COLUMN segment;
ALTER TABLE asset_categories ADD CONSTRAINT uk_asset_categories_scope UNIQUE (asset_type_id, name);

COMMENT ON TABLE asset_categories IS
    'Categoria dentro do tipo de ativo (um nivel). Segmento de acao e FII fica no emissor (issuers).';
