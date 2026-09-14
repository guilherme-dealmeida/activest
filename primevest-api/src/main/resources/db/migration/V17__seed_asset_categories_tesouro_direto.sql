-- Categorias de TESOURO_DIRETO: as cinco famílias oferecidas hoje.
--
-- Aqui, ao contrário da renda fixa bancária, o indexador faz parte do nome do
-- produto — é assim que o Tesouro Nacional os batiza. Ainda assim,
-- assets.indexer continua sendo o lugar de registrar a remuneração efetiva:
-- a categoria diz qual título é, o indexador diz como ele paga.
INSERT INTO asset_categories (asset_type_id, name, description)
SELECT t.id, c.name, c.description
FROM asset_types t
CROSS JOIN (VALUES
    ('TESOURO_SELIC',     'Tesouro Selic'),
    ('TESOURO_PREFIXADO', 'Tesouro Prefixado'),
    ('TESOURO_IPCA',      'Tesouro IPCA+'),
    ('TESOURO_RENDA_MAIS','Tesouro RendA+'),
    ('TESOURO_EDUCA_MAIS','Tesouro Educa+')
) AS c(name, description)
WHERE t.name = 'TESOURO_DIRETO'
ON CONFLICT (asset_type_id, name) DO UPDATE
    SET description = EXCLUDED.description;
