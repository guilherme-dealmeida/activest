// Gera a migration de seed dos emissores (F4 / M3 do docs/planos/PLANO_EMPRESAS_BALANCOS.md)
// a partir do banco de desenvolvimento, depois da curadoria conferida.
//
// Uso: node carga/scripts/gerar_seed_emissores.mjs <arquivo.sql>
//   ex.: node carga/scripts/gerar_seed_emissores.mjs primevest-core/src/main/resources/db/migration/V99__seed_issuers.sql
// Le do conteiner do banco (primevest-db): issuers (todas as companhias + FIIs
// ligados), ligacoes de ACAO e FII, composicao das units e o segmento curado do FII
// (issuers.segment, desde a V31).
import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';

const args = process.argv.slice(2);
const OUT = args.find((a, i) => !a.startsWith('--') && !(i > 0 && args[i - 1].startsWith('--')));
if (!OUT) {
  console.error('Informe o arquivo de saida.');
  process.exit(2);
}
const DB_CONTAINER = 'primevest-db';

function query(sql) {
  const out = execFileSync('docker', ['exec', '-i', DB_CONTAINER, 'sh', '-c', 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'],
    { input: sql, maxBuffer: 256 * 1024 * 1024, encoding: 'utf8' });
  return JSON.parse(out.trim() || '[]') ?? [];
}

const issuers = query(`
SELECT COALESCE(json_agg(x ORDER BY x.kind, x.cnpj), '[]') FROM (
  SELECT i.kind, i.cnpj, i.cvm_code, i.legal_name, i.trade_name, i.registration_status, i.website,
         i.cvm_sector, i.b3_sector, i.b3_subsector, i.b3_segment, i.registration_category, i.listing_segment,
         i.fiscal_year_end_month, i.cvm_segment, i.administrator_name, i.administrator_cnpj, i.management_type,
         i.segment
  FROM issuers i
  WHERE i.kind = 'COMPANY' OR EXISTS (SELECT 1 FROM assets a WHERE a.issuer_id = i.id)
) x;`);

const links = query(`
SELECT COALESCE(json_agg(x ORDER BY x.ticker), '[]') FROM (
  SELECT a.ticker, i.cnpj, a.share_class, a.issuer_link_source AS source, a.isin
  FROM assets a JOIN issuers i ON i.id = a.issuer_id
) x;`);

const units = query(`
SELECT COALESCE(json_agg(x ORDER BY x.ticker, x.share_class), '[]') FROM (
  SELECT a.ticker, u.share_class, u.quantity FROM unit_compositions u JOIN assets a ON a.id = u.asset_id
) x;`);

const q = (v) => (v === null || v === undefined ? 'NULL' : typeof v === 'number' ? String(v) : `'${String(v).replace(/'/g, "''")}'`);
const hoje = new Date().toISOString().slice(0, 10);
const companies = issuers.filter((i) => i.kind === 'COMPANY').length;
const funds = issuers.length - companies;

const cols = ['kind', 'cnpj', 'cvm_code', 'legal_name', 'trade_name', 'registration_status', 'website', 'cvm_sector',
  'b3_sector', 'b3_subsector', 'b3_segment', 'registration_category', 'listing_segment', 'fiscal_year_end_month',
  'segment', 'cvm_segment', 'administrator_name', 'administrator_cnpj', 'management_type'];

let sql = `-- Emissores (F4 / M3 do docs/planos/PLANO_EMPRESAS_BALANCOS.md) — ${companies} companhias e ${funds} FIIs,
-- ${links.length} ligacoes ativo -> emissor e ${units.length} linhas de composicao de units.
--
-- Gerado do banco de desenvolvimento em ${hoje} (carga/scripts/gerar_seed_emissores.mjs), depois da
-- carga das rotinas de emissores (F1), da serie historica da B3 (F2, ISIN) e da ligacao (F3), e da
-- curadoria conferida pelo Guilherme (docs/emissores/Curadoria_Emissores_2026-09-27_corrigida.xlsx).
-- Este arquivo e o estado conferido, nao o caminho: num banco novo, emissores e ligacoes nascem daqui,
-- sem rede; as rotinas de emissores e de ligacao continuam atualizando o que vem da fonte depois.
--
-- Entram todas as companhias importadas (ATIVO + BOLSA, mais as ligadas ao catalogo — D2) e os FIIs
-- ligados a algum ativo do catalogo. O CNPJ do FII e literal (lista conferida, secao 1.6 do plano).
--
-- segment (FII) e a curadoria: o segmento de atuacao (issuers.segment, desde a V31). A importacao do
-- informe nunca o sobrescreve.
--
-- Chave natural: CNPJ para emissor, ticker para ativo. Ids de BIGSERIAL nao valem entre bancos.
-- ON CONFLICT: no banco que ja rodou as rotinas, o dado da fonte fica como esta e so o que e curado
-- (segment) e o que faltar (COALESCE) vem do seed.

INSERT INTO issuers (${cols.join(', ')})
VALUES
`;
sql += issuers.map((i) => '    (' + cols.map((c) => q(i[c])).join(', ') + ')').join(',\n');
sql += `
ON CONFLICT (cnpj) DO UPDATE SET
    segment      = COALESCE(EXCLUDED.segment, issuers.segment),
    b3_sector    = COALESCE(issuers.b3_sector, EXCLUDED.b3_sector),
    b3_subsector = COALESCE(issuers.b3_subsector, EXCLUDED.b3_subsector),
    b3_segment   = COALESCE(issuers.b3_segment, EXCLUDED.b3_segment),
    updated_at   = now();

-- Ligacoes. issuer_link_source guarda por onde a ligacao foi achada (FCA, PREFIXO, B3, ISIN, MANUAL);
-- MANUAL e a curadoria e a rotina de ligacao nunca o sobrescreve. ISIN do COTAHIST.
UPDATE assets a SET
    issuer_id          = i.id,
    share_class        = v.share_class,
    issuer_link_source = v.source,
    isin               = COALESCE(v.isin, a.isin)
FROM (VALUES
`;
sql += links.map((l) => `    (${q(l.ticker)}, ${q(l.cnpj)}, ${q(l.share_class)}, ${q(l.source)}, ${q(l.isin)})`).join(',\n');
sql += `
) AS v(ticker, cnpj, share_class, source, isin)
JOIN issuers i ON i.cnpj = v.cnpj
WHERE a.ticker = v.ticker;

-- Composicao das units (FCA; BPAC11 = 1 ON + 2 PNA pela curadoria).
INSERT INTO unit_compositions (asset_id, share_class, quantity)
SELECT a.id, v.share_class, v.quantity
FROM (VALUES
`;
sql += units.map((u) => `    (${q(u.ticker)}, ${q(u.share_class)}, ${u.quantity})`).join(',\n');
sql += `
) AS v(ticker, share_class, quantity)
JOIN assets a ON a.ticker = v.ticker
ON CONFLICT (asset_id, share_class) DO UPDATE SET quantity = EXCLUDED.quantity;
`;

writeFileSync(OUT, sql.replace(/\n/g, '\r\n'), 'utf8');
const semB3 = issuers.filter((i) => i.kind === 'COMPANY' && !i.b3_segment).map((i) => i.legal_name);
console.log(`${OUT}: ${companies} companhias, ${funds} FIIs, ${links.length} ligacoes, ${units.length} linhas de unit`);
console.log(`FIIs sem segmento: ${issuers.filter((i) => i.kind === 'FII' && !i.segment).length}`);
console.log(`Companhias sem classificacao B3 (${semB3.length}): ${semB3.join('; ')}`);
