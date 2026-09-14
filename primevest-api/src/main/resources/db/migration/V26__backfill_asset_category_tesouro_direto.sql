-- Classifica os 130 títulos do Tesouro Direto por família.
--
-- Aqui, ao contrário das ações, a categoria está no próprio nome do título: o
-- Tesouro Nacional batiza o papel pela família ("Tesouro Selic 2031"). Então o
-- mapeamento é por prefixo do nome, determinístico, sem julgamento nenhum.
--
-- As duas variações com juros semestrais caem na mesma família do seu par sem
-- cupom, porque o que muda entre elas é o fluxo de pagamento, não o indexador:
--   Tesouro Prefixado e Tesouro Prefixado com Juros Semestrais  -> TESOURO_PREFIXADO
--   Tesouro IPCA+     e Tesouro IPCA+ com Juros Semestrais      -> TESOURO_IPCA
-- Quem distingue LTN de NTN-F é assets.rate_type junto com maturity_date, já
-- preenchidos pela V22 — a categoria não precisa repetir isso.
--
-- Distribuição esperada:
--   TESOURO_PREFIXADO     43   (27 sem cupom + 16 com juros semestrais)
--   TESOURO_IPCA          32   (10 sem cupom + 22 com juros semestrais)
--   TESOURO_SELIC         21
--   TESOURO_EDUCA_MAIS    19
--   TESOURO_RENDA_MAIS     8
--   TESOURO_IGPM           7
--                        ---
--                         130

-- TESOURO_IGPM não existia entre as cinco categorias da V17, que cobriu só as
-- famílias em oferta hoje. Os sete títulos indexados ao IGP-M (NTN-C) são
-- históricos — o último vence em 2031 e nenhum é emitido desde 2006 — mas estão
-- no catálogo e precisam de casa própria. Enquadrá-los em TESOURO_IPCA os
-- misturaria com papéis de outro indexador.
INSERT INTO asset_categories (asset_type_id, name, description)
SELECT t.id, 'TESOURO_IGPM', 'Tesouro IGPM+'
FROM asset_types t
WHERE t.name = 'TESOURO_DIRETO'
ON CONFLICT (asset_type_id, name) DO UPDATE
    SET description = EXCLUDED.description;

-- asset_category_id IS NULL mantém o script repetível e preserva qualquer
-- classificação feita à mão depois.
WITH familia (prefixo, categoria) AS (VALUES
    ('Tesouro Selic%'::varchar,     'TESOURO_SELIC'::varchar),
    ('Tesouro Prefixado%',          'TESOURO_PREFIXADO'),
    ('Tesouro IPCA+%',              'TESOURO_IPCA'),
    ('Tesouro IGPM+%',              'TESOURO_IGPM'),
    ('Tesouro Renda+%',             'TESOURO_RENDA_MAIS'),
    ('Tesouro Educa+%',             'TESOURO_EDUCA_MAIS')
)
UPDATE assets a
SET asset_category_id = c.id
FROM familia f
JOIN asset_types t      ON t.name = 'TESOURO_DIRETO'
JOIN asset_categories c ON c.asset_type_id = t.id
                       AND c.name = f.categoria
WHERE a.asset_type_id = t.id
  AND a.name LIKE f.prefixo
  AND a.asset_category_id IS NULL;
