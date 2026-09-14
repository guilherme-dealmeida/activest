-- Categorias de FII.
--
-- Base: classificação ANBIMA para fundos imobiliários, que tem dois níveis —
-- mandato (Renda, Desenvolvimento, Títulos e Valores Mobiliários, Híbridos) e
-- segmento do imóvel (lajes, shoppings, logística...). asset_categories é um
-- nível só, então os dois foram achatados no que o investidor usa para filtrar:
-- o que o fundo detém.
--
-- FUNDO_DE_FUNDOS não consta da lista da ANBIMA, mas é categoria consolidada
-- no mercado e sem ela não há onde classificar um FoF.
INSERT INTO asset_categories (asset_type_id, name, description)
SELECT t.id, c.name, c.description
FROM asset_types t
CROSS JOIN (VALUES
    -- Segmento do imóvel (ANBIMA)
    ('LAJES_CORPORATIVAS', 'Lajes Corporativas'),
    ('SHOPPINGS',          'Shoppings'),
    ('LOGISTICA',          'Imóveis Logísticos'),
    ('RESIDENCIAL',        'Imóveis Residenciais'),
    ('AGENCIAS_BANCARIAS', 'Agências Bancárias'),
    ('EDUCACIONAL',        'Imóveis Educacionais'),
    ('HOSPITALAR',         'Hospital'),
    ('HOTEL',              'Hotel'),
    -- Mandato (ANBIMA)
    ('TITULOS_E_VALORES_MOBILIARIOS', 'Títulos e Valores Mobiliários'),  -- os "fundos de papel"
    ('DESENVOLVIMENTO',    'Desenvolvimento'),
    ('HIBRIDO',            'Híbrido'),
    -- Uso de mercado
    ('FUNDO_DE_FUNDOS',    'Fundo de Fundos'),
    ('OUTROS',             'Outros')
) AS c(name, description)
WHERE t.name = 'FII'
ON CONFLICT (asset_type_id, name) DO UPDATE
    SET description = EXCLUDED.description;
