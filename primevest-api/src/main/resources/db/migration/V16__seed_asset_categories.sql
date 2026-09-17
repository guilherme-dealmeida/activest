-- Categorias dentro de cada tipo de ativo.
--
-- As 48 categorias chegaram a este formato por etapas: as de renda fixa e
-- Tesouro nasceram com os tipos, as de ação vieram da classificação setorial da
-- B3 e as de FII dos segmentos de mercado. Este arquivo é o resultado, não o
-- caminho — reconstruir o caminho a cada banco novo não traz nada além de mais
-- migrations para dar errado.
--
-- O tipo é resolvido por nome, como em todos os seeds do projeto. Ids de
-- BIGSERIAL só valem num banco que rodou as migrations nesta ordem exata;
-- fixá-los aqui quebraria em qualquer outro.
INSERT INTO asset_categories (name, description, asset_type_id)
SELECT p.nome, p.descricao, t.id
FROM (VALUES
    ('CDB'::varchar,                            'Certificado de Depósito Bancário'::varchar,          'RENDA_FIXA'::varchar),
    ('RDB',                                     'Recibo de Depósito Bancário',                        'RENDA_FIXA'),
    ('LCI',                                     'Letra de Crédito Imobiliário',                       'RENDA_FIXA'),
    ('LCA',                                     'Letra de Crédito do Agronegócio',                    'RENDA_FIXA'),
    ('LC',                                      'Letra de Câmbio',                                    'RENDA_FIXA'),
    ('DPGE',                                    'Depósito a Prazo com Garantia Especial',             'RENDA_FIXA'),
    ('POUPANCA',                                'Poupança',                                           'RENDA_FIXA'),
    ('LF',                                      'Letra Financeira',                                   'RENDA_FIXA'),
    ('LIG',                                     'Letra Imobiliária Garantida',                        'RENDA_FIXA'),
    ('CCB',                                     'Cédula de Crédito Bancário',                         'RENDA_FIXA'),
    ('DEBENTURE',                               'Debênture',                                          'RENDA_FIXA'),
    ('DEBENTURE_INCENTIVADA',                   'Debênture Incentivada',                              'RENDA_FIXA'),
    ('CRI',                                     'Certificado de Recebíveis Imobiliários',             'RENDA_FIXA'),
    ('CRA',                                     'Certificado de Recebíveis do Agronegócio',           'RENDA_FIXA'),
    ('NOTA_COMERCIAL',                          'Nota Comercial',                                     'RENDA_FIXA'),
    ('LAJES_CORPORATIVAS',                      'Lajes Corporativas',                                 'FII'),
    ('SHOPPINGS',                               'Shoppings',                                          'FII'),
    ('LOGISTICA',                               'Imóveis Logísticos',                                 'FII'),
    ('RESIDENCIAL',                             'Imóveis Residenciais',                               'FII'),
    ('AGENCIAS_BANCARIAS',                      'Agências Bancárias',                                 'FII'),
    ('EDUCACIONAL',                             'Imóveis Educacionais',                               'FII'),
    ('HOSPITALAR',                              'Hospital',                                           'FII'),
    ('HOTEL',                                   'Hotel',                                              'FII'),
    ('TITULOS_E_VALORES_MOBILIARIOS',           'Títulos e Valores Mobiliários',                      'FII'),
    ('DESENVOLVIMENTO',                         'Desenvolvimento',                                    'FII'),
    ('HIBRIDO',                                 'Híbrido',                                            'FII'),
    ('FUNDO_DE_FUNDOS',                         'Fundo de Fundos',                                    'FII'),
    ('OUTROS',                                  'Outros',                                             'FII'),
    ('PETROLEO_GAS_BIOCOMBUSTIVEIS',            'Petróleo, Gás e Biocombustíveis',                    'ACAO'),
    ('MATERIAIS_BASICOS',                       'Materiais Básicos',                                  'ACAO'),
    ('BENS_INDUSTRIAIS',                        'Bens Industriais',                                   'ACAO'),
    ('CONSUMO_NAO_CICLICO',                     'Consumo não Cíclico',                                'ACAO'),
    ('CONSUMO_CICLICO',                         'Consumo Cíclico',                                    'ACAO'),
    ('SAUDE',                                   'Saúde',                                              'ACAO'),
    ('TECNOLOGIA_DA_INFORMACAO',                'Tecnologia da Informação',                           'ACAO'),
    ('COMUNICACOES',                            'Comunicações',                                       'ACAO'),
    ('UTILIDADE_PUBLICA',                       'Utilidade Pública',                                  'ACAO'),
    ('FINANCEIRO',                              'Financeiro',                                         'ACAO'),
    ('OUTROS',                                  'Outros',                                             'ACAO'),
    ('TESOURO_SELIC',                           'Tesouro Selic',                                      'TESOURO_DIRETO'),
    ('TESOURO_PREFIXADO',                       'Tesouro Prefixado',                                  'TESOURO_DIRETO'),
    ('TESOURO_IPCA',                            'Tesouro IPCA+',                                      'TESOURO_DIRETO'),
    ('TESOURO_RENDA_MAIS',                      'Tesouro RendA+',                                     'TESOURO_DIRETO'),
    ('TESOURO_EDUCA_MAIS',                      'Tesouro Educa+',                                     'TESOURO_DIRETO'),
    ('IMOBILIARIO',                             'Imobiliário',                                        'ACAO'),
    ('TESOURO_IGPM',                            'Tesouro IGPM+',                                      'TESOURO_DIRETO'),
    ('IMOVEIS_COMERCIAIS',                      'Imóveis Comerciais',                                 'FII'),
    ('VAREJO',                                  'Varejo',                                             'FII')
) AS p(nome, descricao, tipo)
JOIN asset_types t ON t.name = p.tipo
ON CONFLICT (asset_type_id, name) DO UPDATE
    SET description = EXCLUDED.description;
