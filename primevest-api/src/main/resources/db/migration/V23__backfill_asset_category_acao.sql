-- Classifica as ações cujo setor de origem tem destino único na taxonomia da B3.
--
-- assets.sector veio da BrAPI, que usa a taxonomia FactSet (a mesma da
-- TradingView): 20 setores em inglês. asset_categories para ACAO guarda os 11
-- Setores Econômicos da B3, em português. São dois sistemas de classificação
-- concorrentes, não o mesmo em granularidades diferentes.
--
-- Dos 20 setores FactSet presentes no catálogo, 12 caem inteiros num único
-- setor da B3 — são os 539 papéis tratados aqui. O mapeamento é N:1: Health
-- Services e Health Technology viram ambos SAUDE; Producer Manufacturing e
-- Transportation viram ambos BENS_INDUSTRIAIS.
--
-- Os outros 8 setores ficam DE FORA de propósito, porque um mesmo setor de
-- origem se parte em setores B3 diferentes e a decisão depende da empresa:
--
--   Consumer Non-Durables  ABEV3 (bebida, não cíclico) x ALPA3 (calçado, cíclico)
--   Retail Trade           AMER3 (varejo, cíclico)     x ASAI3 (supermercado, não cíclico)
--   Industrial Services    AZEV3 (construção pesada) x CALI3 (incorporação) x LOGG3 (imóveis → Financeiro)
--   Distribution Services  BEEF3 (frigorífico) x AGXY3 (insumos agro) x DMVF3 (farmácia)
--   Electronic Technology  POSI3 (eletrônica) x EMBJ3 (aeroespacial → Bens Industriais)
--   Process Industries, Commercial Services, Consumer Services  mesma situação
--
-- São 242 papéis que precisam de classificação manual, uma a uma. Chutar um
-- destino para eles colocaria dado errado no banco com cara de dado certo.
--
-- A cláusula asset_category_id IS NULL torna o script repetível e, mais
-- importante, impede que uma classificação feita à mão depois seja
-- sobrescrita por este mapeamento automático.

WITH mapa (sector, categoria) AS (VALUES
    ('Finance',                'FINANCEIRO'),
    ('Utilities',              'UTILIDADE_PUBLICA'),
    ('Producer Manufacturing', 'BENS_INDUSTRIAIS'),
    ('Transportation',         'BENS_INDUSTRIAIS'),
    ('Non-Energy Minerals',    'MATERIAIS_BASICOS'),
    ('Consumer Durables',      'CONSUMO_CICLICO'),
    ('Health Services',        'SAUDE'),
    ('Health Technology',      'SAUDE'),
    ('Technology Services',    'TECNOLOGIA_DA_INFORMACAO'),
    ('Communications',         'COMUNICACOES'),
    ('Energy Minerals',        'PETROLEO_GAS_BIOCOMBUSTIVEIS'),
    ('Miscellaneous',          'OUTROS')
)
UPDATE assets a
SET asset_category_id = c.id
FROM mapa m
JOIN asset_types t      ON t.name = 'ACAO'
JOIN asset_categories c ON c.asset_type_id = t.id
                       AND c.name = m.categoria
WHERE a.asset_type_id = t.id
  AND a.sector = m.sector
  AND a.asset_category_id IS NULL;
