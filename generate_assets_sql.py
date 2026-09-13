import json
import os
import re

stocksFile = r'C:\Users\guilh\.gemini\antigravity\brain\3ea2903e-9822-442d-a7e1-68f9604eaec8\.system_generated\steps\1045\content.md'
fundsFile = r'C:\Users\guilh\.gemini\antigravity\brain\3ea2903e-9822-442d-a7e1-68f9604eaec8\.system_generated\steps\1046\content.md'
outputFile = r'C:\Users\guilh\.gemini\antigravity\scratch\primevest\primevest-api\src\main\resources\db\migration\V19__add_custom_column_and_b3_assets.sql'

def parse_md_json(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    parts = content.split('---')
    json_str = '---'.join(parts[1:]).strip() if len(parts) > 1 else content
    first = json_str.find('{')
    last = json_str.rfind('}')
    if first == -1 or last == -1:
        raise Exception('JSON not found')
    valid = json_str[first:last+1]
    return json.loads(valid)

stocksData = parse_md_json(stocksFile)
fundsData = parse_md_json(fundsFile)

sql = '''ALTER TABLE assets ADD COLUMN IF NOT EXISTS custom BOOLEAN DEFAULT false;
UPDATE assets SET custom = true;

INSERT INTO asset_categories (id, name, asset_type_id, description, active) VALUES 
(26, 'PETROLEO', 4, 'Petróleo', true),
(27, 'MINERACAO', 4, 'Mineração', true),
(28, 'VAREJO', 4, 'Varejo', true),
(29, 'SAUDE', 4, 'Saúde', true),
(30, 'INDUSTRIAL', 4, 'Industrial', true),
(31, 'TRANSPORTE', 4, 'Transporte', true),
(32, 'AGRONEGOCIO', 4, 'Agronegócio', true),
(33, 'CONSTRUCAO', 4, 'Construção', true),
(34, 'EDUCACAO', 4, 'Educação', true),
(35, 'ALIMENTOS', 4, 'Alimentos', true),
(36, 'PAPEL_CELULOSE', 4, 'Papel e Celulose', true),
(37, 'QUIMICA', 4, 'Química', true),
(38, 'OUTROS_RV', 4, 'Outros Renda Variável', true),
(39, 'LOGISTICA', 3, 'Logística', true),
(40, 'SHOPPING', 3, 'Shopping', true),
(41, 'ESCRITORIO', 3, 'Escritório', true),
(42, 'RESIDENCIAL', 3, 'Residencial', true),
(43, 'AGRO_FII', 3, 'Agro', true),
(44, 'ETF_NACIONAL', 4, 'ETF Nacional', true),
(45, 'BDR_NACIONAL', 4, 'BDR Nacional', true),
(46, 'RENDA_PLUS', 2, 'Tesouro Renda+', true)
ON CONFLICT (id) DO NOTHING;

'''

def escape_sql(s):
    if not s: return 'NULL'
    return "'" + str(s).replace("'", "''") + "'"

stocksCount = 0
fiiCount = 0
etfCount = 0
tesouroCount = 0

for stock in stocksData.get('stocks', []):
    if stock.get('type') == 'stock':
        sector = stock.get('sector', '')
        subsector = stock.get('subsector', '')
        catId = 38
        
        if sector == 'Energy Minerals': catId = 26
        elif sector == 'Non-Energy Minerals': catId = 27
        elif sector in ['Retail Trade', 'Distribution Services']: catId = 28
        elif sector in ['Health Services', 'Health Technology']: catId = 29
        elif sector in ['Producer Manufacturing', 'Industrial Services', 'Consumer Durables']: catId = 30
        elif sector == 'Transportation': catId = 31
        elif sector == 'Process Industries': catId = 32
        elif sector == 'Finance' and 'Incorporações' in subsector: catId = 33
        elif sector in ['Commercial Services', 'Consumer Services'] and 'Educacionais' in subsector: catId = 34
        elif sector == 'Consumer Non-Durables': catId = 35
        elif sector == 'Communications': catId = 20
        elif sector == 'Utilities': catId = 18
        elif sector == 'Finance' and 'Bancos' in subsector: catId = 15
        elif sector == 'Finance' and 'Segurad' in subsector: catId = 17
        elif sector == 'Finance': catId = 19
        elif sector in ['Technology Services', 'Electronic Technology']: catId = 22

        sql += f"INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, sector, active, custom) VALUES ({escape_sql(stock.get('stock'))}, {escape_sql(stock.get('name'))}, 4, {catId}, {escape_sql(stock.get('sector'))}, true, false) ON CONFLICT (ticker) DO UPDATE SET custom = false;\n"
        stocksCount += 1

for fund in fundsData.get('stocks', []):
    if fund.get('subType') == 'fii':
        subsector = fund.get('subsector', '') or ''
        catId = 14
        if 'Logística' in subsector: catId = 39
        elif 'Shopping' in subsector: catId = 40
        elif 'Escritório' in subsector or 'Escritorio' in subsector: catId = 41
        elif 'Residencial' in subsector: catId = 42
        elif 'Multicategoria' in subsector: catId = 14
        elif 'Outros' in subsector: catId = 12
        elif 'Varejo' in subsector: catId = 11
        elif 'Títulos e Val. Mob.' in subsector: catId = 12
        
        sql += f"INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, sector, active, custom) VALUES ({escape_sql(fund.get('stock'))}, {escape_sql(fund.get('name'))}, 3, {catId}, {escape_sql(fund.get('sector'))}, true, false) ON CONFLICT (ticker) DO UPDATE SET custom = false;\n"
        fiiCount += 1
    elif fund.get('subType') == 'etf':
        sql += f"INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, sector, active, custom) VALUES ({escape_sql(fund.get('stock'))}, {escape_sql(fund.get('name'))}, 5, 23, {escape_sql(fund.get('sector'))}, true, false) ON CONFLICT (ticker) DO UPDATE SET custom = false;\n"
        etfCount += 1
    elif fund.get('subType') == 'bdr':
        sql += f"INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, sector, active, custom) VALUES ({escape_sql(fund.get('stock'))}, {escape_sql(fund.get('name'))}, 5, 24, {escape_sql(fund.get('sector'))}, true, false) ON CONFLICT (ticker) DO UPDATE SET custom = false;\n"
        stocksCount += 1

tesouros = [
    {'name': 'Tesouro Selic 2025', 'cat': 8}, {'name': 'Tesouro Selic 2026', 'cat': 8}, {'name': 'Tesouro Selic 2027', 'cat': 8}, {'name': 'Tesouro Selic 2028', 'cat': 8}, {'name': 'Tesouro Selic 2029', 'cat': 8},
    {'name': 'Tesouro IPCA+ 2026', 'cat': 9}, {'name': 'Tesouro IPCA+ 2029', 'cat': 9}, {'name': 'Tesouro IPCA+ 2032', 'cat': 9}, {'name': 'Tesouro IPCA+ 2035', 'cat': 9}, {'name': 'Tesouro IPCA+ 2040', 'cat': 9}, {'name': 'Tesouro IPCA+ 2045', 'cat': 9}, {'name': 'Tesouro IPCA+ 2050', 'cat': 9}, {'name': 'Tesouro IPCA+ 2055', 'cat': 9},
    {'name': 'Tesouro Prefixado 2025', 'cat': 10}, {'name': 'Tesouro Prefixado 2026', 'cat': 10}, {'name': 'Tesouro Prefixado 2027', 'cat': 10}, {'name': 'Tesouro Prefixado 2028', 'cat': 10}, {'name': 'Tesouro Prefixado 2031', 'cat': 10}, {'name': 'Tesouro Prefixado 2032', 'cat': 10},
    {'name': 'Tesouro IPCA+ com Juros Semestrais 2026', 'cat': 9}, {'name': 'Tesouro IPCA+ com Juros Semestrais 2032', 'cat': 9}, {'name': 'Tesouro IPCA+ com Juros Semestrais 2035', 'cat': 9}, {'name': 'Tesouro IPCA+ com Juros Semestrais 2040', 'cat': 9}, {'name': 'Tesouro IPCA+ com Juros Semestrais 2045', 'cat': 9}, {'name': 'Tesouro IPCA+ com Juros Semestrais 2050', 'cat': 9}, {'name': 'Tesouro IPCA+ com Juros Semestrais 2055', 'cat': 9},
    {'name': 'Tesouro Renda+ 2030', 'cat': 46}, {'name': 'Tesouro Renda+ 2035', 'cat': 46}, {'name': 'Tesouro Renda+ 2040', 'cat': 46}, {'name': 'Tesouro Renda+ 2045', 'cat': 46}, {'name': 'Tesouro Renda+ 2050', 'cat': 46}, {'name': 'Tesouro Renda+ 2055', 'cat': 46}, {'name': 'Tesouro Renda+ 2060', 'cat': 46}, {'name': 'Tesouro Renda+ 2065', 'cat': 46},
    {'name': 'Tesouro RendA+ Aposentadoria Extra 2030', 'cat': 46}, {'name': 'Tesouro RendA+ Aposentadoria Extra 2035', 'cat': 46}, {'name': 'Tesouro RendA+ Aposentadoria Extra 2040', 'cat': 46}, {'name': 'Tesouro RendA+ Aposentadoria Extra 2045', 'cat': 46}, {'name': 'Tesouro RendA+ Aposentadoria Extra 2050', 'cat': 46}, {'name': 'Tesouro RendA+ Aposentadoria Extra 2055', 'cat': 46}, {'name': 'Tesouro RendA+ Aposentadoria Extra 2060', 'cat': 46}, {'name': 'Tesouro RendA+ Aposentadoria Extra 2065', 'cat': 46}
]

for t in tesouros:
    sql += f"INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active, custom) VALUES ({escape_sql(t['name'])}, {escape_sql(t['name'])}, 2, {t['cat']}, true, false) ON CONFLICT (ticker) DO UPDATE SET custom = false;\n"
    tesouroCount += 1

os.makedirs(os.path.dirname(outputFile), exist_ok=True)
with open(outputFile, 'w', encoding='utf-8') as f:
    f.write(sql)

print(json.dumps({
    'stocksCount': stocksCount,
    'fiiCount': fiiCount,
    'etfCount': etfCount,
    'tesouroCount': tesouroCount,
    'total': stocksCount + fiiCount + etfCount + tesouroCount
}))
