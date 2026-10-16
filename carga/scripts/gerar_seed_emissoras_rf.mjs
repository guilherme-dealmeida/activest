// Gera a migration de seed das emissoras e distribuidoras de renda fixa (E3 do
// docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, secao 3.2) a partir do banco de desenvolvimento, depois da integracao
// BCB + CVM (POST /api/v1/admin/fixed-income-issuers) e da curadoria conferida
// (docs/emissores/Curadoria_Grupos_RF_2026-10-09.xlsx).
//
// Uso: node carga/scripts/gerar_seed_emissoras_rf.mjs <arquivo.sql>
//   ex.: node carga/scripts/gerar_seed_emissoras_rf.mjs primevest-core/src/main/resources/db/migration/V55__seed_emissoras_renda_fixa.sql
// Le do conteiner do banco (primevest-db): fixed_income_groups e fixed_income_issuers (menos as duas linhas fixas da
// V54, Tesouro Nacional e Instituicao nao listada).
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

const groups = query(`
SELECT COALESCE(json_agg(x ORDER BY x.bcb_code), '[]') FROM (
  SELECT g.bcb_code, g.name, g.name_source, g.description, g.leader_cnpj_root, g.logo_ticker, g.extinct
  FROM fixed_income_groups g
) x;`);

const issuers = query(`
SELECT COALESCE(json_agg(x ORDER BY x.cnpj_root), '[]') FROM (
  SELECT i.kind, i.cnpj_root, i.cnpj, i.legal_name, i.trade_name, i.segment, i.status, i.website, i.guarantor,
         i.sale_rule, g.bcb_code AS group_code, i.group_source, i.sources,
         to_char(i.source_updated_at, 'YYYY-MM-DD HH24:MI:SS') AS source_updated_at
  FROM fixed_income_issuers i LEFT JOIN fixed_income_groups g ON g.id = i.group_id
  WHERE i.cnpj_root IS NOT NULL
) x;`);

const semCodigo = groups.filter((g) => !g.bcb_code);
if (semCodigo.length) {
  // A chave natural do grupo no seed e o codigo do BCB; grupo so de curadoria ainda nao existe
  console.error(`Grupos sem codigo do BCB (${semCodigo.length}): ${semCodigo.map((g) => g.name).join('; ')}`);
  process.exit(1);
}

const q = (v) => (v === null || v === undefined ? 'NULL'
  : typeof v === 'number' || typeof v === 'boolean' ? String(v).toUpperCase()
  : `'${String(v).replace(/'/g, "''")}'`);
const hoje = new Date().toISOString().slice(0, 10);
const porTipo = Object.entries(issuers.reduce((m, i) => ({ ...m, [i.kind]: (m[i.kind] ?? 0) + 1 }), {}))
  .map(([k, n]) => `${k} ${n}`).join(', ');
const manuais = groups.filter((g) => g.name_source === 'MANUAL').length;
const ligacoesManuais = issuers.filter((i) => i.group_source === 'MANUAL').length;

const gcols = ['bcb_code', 'name', 'name_source', 'description', 'leader_cnpj_root', 'logo_ticker', 'extinct'];
const icols = ['kind', 'cnpj_root', 'cnpj', 'legal_name', 'trade_name', 'segment', 'status', 'website', 'guarantor',
  'sale_rule', 'group_code', 'group_source', 'sources', 'source_updated_at'];

let sql = `-- Emissoras e distribuidoras de renda fixa (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, secao 3.2) — ${groups.length}
-- grupos (${manuais} com nome curado) e ${issuers.length} empresas (${porTipo}); ${ligacoesManuais} ligacoes
-- empresa -> grupo por curadoria.
--
-- Gerado do banco de desenvolvimento em ${hoje} (carga/scripts/gerar_seed_emissoras_rf.mjs), depois da integracao
-- BCB (IF.data, data-base mais recente publicada) + CVM (intermediarios, companhias abertas, securitizadoras) e da
-- curadoria conferida pelo Guilherme (docs/emissores/Curadoria_Grupos_RF_2026-10-09.xlsx): nomes dos grupos, empresas
-- inativas no grupo que tinham, grupos extintos, regra de saida da Nu Financeira (UEPS) e o logo de cada grupo.
-- Este arquivo e o estado conferido, nao o caminho: num banco novo, empresas e grupos nascem daqui, sem rede; a rotina
-- mensal continua atualizando o que vem da fonte.
--
-- Chave natural: codigo do conglomerado do BCB (grupo) e raiz do CNPJ (empresa). Ids de BIGSERIAL nao valem entre
-- bancos. ON CONFLICT: no banco que ja rodou a integracao, o dado da fonte fica como esta e o seed traz o que e
-- curadoria (nome MANUAL, ligacao MANUAL, regra de saida, logo) e o que faltar.

INSERT INTO fixed_income_groups (${gcols.join(', ')})
VALUES
`;
sql += groups.map((g) => '    (' + gcols.map((c) => q(g[c])).join(', ') + ')').join(',\n');
sql += `
ON CONFLICT (bcb_code) DO UPDATE SET
    name             = CASE WHEN EXCLUDED.name_source = 'MANUAL' THEN EXCLUDED.name ELSE fixed_income_groups.name END,
    name_source      = CASE WHEN EXCLUDED.name_source = 'MANUAL' THEN 'MANUAL' ELSE fixed_income_groups.name_source END,
    description      = COALESCE(fixed_income_groups.description, EXCLUDED.description),
    leader_cnpj_root = COALESCE(fixed_income_groups.leader_cnpj_root, EXCLUDED.leader_cnpj_root),
    logo_ticker      = COALESCE(EXCLUDED.logo_ticker, fixed_income_groups.logo_ticker),
    extinct          = EXCLUDED.extinct OR fixed_income_groups.extinct,
    updated_at       = now();

INSERT INTO fixed_income_issuers (kind, cnpj_root, cnpj, legal_name, trade_name, segment, status, website, guarantor,
                                  sale_rule, group_id, group_source, sources, source_updated_at)
SELECT v.kind, v.cnpj_root, v.cnpj, v.legal_name, v.trade_name, v.segment, v.status, v.website, v.guarantor,
       v.sale_rule, g.id, v.group_source, v.sources, v.source_updated_at::timestamp
FROM (VALUES
`;
sql += issuers.map((i) => '    (' + icols.map((c) => q(i[c])).join(', ') + ')').join(',\n');
sql += `
) AS v(${icols.join(', ')})
LEFT JOIN fixed_income_groups g ON g.bcb_code = v.group_code
ON CONFLICT (cnpj_root) DO UPDATE SET
    cnpj         = COALESCE(fixed_income_issuers.cnpj, EXCLUDED.cnpj),
    trade_name   = COALESCE(fixed_income_issuers.trade_name, EXCLUDED.trade_name),
    segment      = COALESCE(fixed_income_issuers.segment, EXCLUDED.segment),
    status       = COALESCE(fixed_income_issuers.status, EXCLUDED.status),
    website      = COALESCE(fixed_income_issuers.website, EXCLUDED.website),
    sale_rule    = CASE WHEN EXCLUDED.sale_rule <> 'PROPORCIONAL' THEN EXCLUDED.sale_rule
                        ELSE fixed_income_issuers.sale_rule END,
    group_id     = CASE WHEN EXCLUDED.group_source = 'MANUAL' OR fixed_income_issuers.group_id IS NULL
                        THEN EXCLUDED.group_id ELSE fixed_income_issuers.group_id END,
    group_source = CASE WHEN EXCLUDED.group_source = 'MANUAL' OR fixed_income_issuers.group_id IS NULL
                        THEN EXCLUDED.group_source ELSE fixed_income_issuers.group_source END,
    updated_at   = now();
`;

writeFileSync(OUT, sql.replace(/\n/g, '\r\n'), 'utf8');
console.log(`${OUT}: ${groups.length} grupos (${manuais} curados), ${issuers.length} empresas (${porTipo}), `
  + `${ligacoesManuais} ligacoes por curadoria`);
