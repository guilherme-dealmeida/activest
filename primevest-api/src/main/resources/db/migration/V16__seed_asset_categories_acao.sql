-- Categorias de AÇAO: setores econômicos da classificação setorial da B3.
--
-- A B3 classifica em três níveis: setor econômico -> subsetor -> segmento.
-- asset_categories é um nível só, e o setor econômico é o corte certo aqui:
-- são 11 valores estáveis, contra dezenas de subsetores e quase uma centena
-- de segmentos — granularidade que não caberia num filtro de tela.
--
-- A base da B3 é atualizada semanalmente; se um setor for criado ou renomeado,
-- entra por migration nova (o ON CONFLICT abaixo atualiza a descrição).
INSERT INTO asset_categories (asset_type_id, name, description)
SELECT t.id, c.name, c.description
FROM asset_types t
CROSS JOIN (VALUES
    ('PETROLEO_GAS_BIOCOMBUSTIVEIS', 'Petróleo, Gás e Biocombustíveis'),
    ('MATERIAIS_BASICOS',            'Materiais Básicos'),
    ('BENS_INDUSTRIAIS',             'Bens Industriais'),
    ('CONSUMO_NAO_CICLICO',          'Consumo não Cíclico'),
    ('CONSUMO_CICLICO',              'Consumo Cíclico'),
    ('SAUDE',                        'Saúde'),
    ('TECNOLOGIA_DA_INFORMACAO',     'Tecnologia da Informação'),
    ('COMUNICACOES',                 'Comunicações'),
    ('UTILIDADE_PUBLICA',            'Utilidade Pública'),
    ('FINANCEIRO',                   'Financeiro'),
    ('OUTROS',                       'Outros')
) AS c(name, description)
WHERE t.name = 'ACAO'
ON CONFLICT (asset_type_id, name) DO UPDATE
    SET description = EXCLUDED.description;
