// Consolida as migrations: le um banco ja migrado e escreve o conjunto novo em que cada tabela nasce pronta
// (um CREATE TABLE com colunas, defaults, constraints e indices finais) e cada seed e um INSERT com os valores
// finais. Usado na renomeacao para Activest (docs/planos/PLANO_RENOMEACAO_ACTIVEST.md, secao 4.2).
//
// Uso (na raiz, no Windows):
//   node carga/scripts/migrations/consolidate.mjs --db <conteiner> --database <banco> --from <pasta das migrations antigas> --out <pasta>
// O banco de origem deve ser o que as migrations antigas produzem num banco vazio (D16), e nao um banco com
// dados de integracao: tudo o que estiver nas tabelas de seed vira seed.
//
// Colunas na ordem: chave primaria, dados, chaves estrangeiras, auditoria (created_at, updated_at, deleted_at) - D15.
// Comentarios: o bloco que antecedia o CREATE TABLE na migration de origem, os comentarios de coluna e os blocos
// que antecediam cada ALTER TABLE, com a versao de onde vieram.
import { execFileSync } from 'node:child_process';
import { readdirSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

const args = process.argv.slice(2);
const opt = (name, def) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : def; };
const DB = opt('--db'); const DATABASE = opt('--database'); const FROM = opt('--from'); const OUT = opt('--out');
if (!DB || !DATABASE || !FROM || !OUT) { console.error('Uso: --db --database --from --out'); process.exit(2); }

function psql(sql) {
  return execFileSync('docker', ['exec', '-i', DB, 'psql', '-U', 'postgres', '-d', DATABASE, '-At', '-v', 'ON_ERROR_STOP=1'],
    { input: sql, maxBuffer: 1024 * 1024 * 1024, encoding: 'utf8' });
}
const json = (sql) => JSON.parse(psql(`SELECT COALESCE(json_agg(x), '[]') FROM (${sql}) x;`).trim());

const AUDIT = ['created_at', 'updated_at', 'deleted_at'];
const IGNORE = ['flyway_schema_history'];
// Arquivos de seed, na ordem das chaves estrangeiras.
const SEEDS = [
  ['V2__seed_reference.sql', 'Dados de referencia: tipos e categorias de ativo, IR retido nos proventos, indices de mercado e tarefas.',
    ['asset_types', 'asset_categories', 'withholding_tax_rules', 'market_indices', 'task_types', 'task_type_attributes', 'tasks', 'task_attributes']],
  ['V3__seed_issuers.sql', 'Emissores (companhias e FIIs) conferidos na curadoria (docs/planos/PLANO_EMPRESAS_BALANCOS.md).',
    ['issuers']],
  ['V4__seed_assets.sql', 'Catalogo de ativos, ja com nomes, situacao, emissor e ISIN finais; parametros do Tesouro, historico de preco semeado, composicao das units, IR/IOF da renda fixa e benchmarks.',
    ['assets', 'asset_terms', 'asset_price_history', 'unit_compositions', 'fixed_income_tax_rules', 'benchmarks']],
  ['V5__seed_fixed_income_issuers.sql', 'Grupos e emissoras/distribuidoras de renda fixa (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md).',
    ['fixed_income_groups', 'fixed_income_issuers']],
  ['V6__seed_dev_user.sql', 'Usuario administrador do ambiente de desenvolvimento, a carteira dele e a reserva da estrategia.',
    ['users', 'portfolios', 'strategy_reservations']],
];
// Colunas que nao vao para o seed: o default gera (id aleatorio que ninguem referencia).
const OMIT = { strategy_reservations: ['id'] };

// ---------------------------------------------------------------- catalogo
const tables = json(`SELECT c.relname AS name FROM pg_class c WHERE c.relkind = 'r' AND c.relnamespace = 'public'::regnamespace`)
  .map((t) => t.name).filter((t) => !IGNORE.includes(t));
const columns = json(`
  SELECT c.relname AS table, a.attname AS name, a.attnum AS num, format_type(a.atttypid, a.atttypmod) AS type,
         a.attnotnull AS notnull, pg_get_expr(d.adbin, d.adrelid) AS def, t.typcategory AS cat
  FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid JOIN pg_type t ON t.oid = a.atttypid
  LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
  WHERE c.relkind = 'r' AND c.relnamespace = 'public'::regnamespace AND a.attnum > 0 AND NOT a.attisdropped
  ORDER BY c.relname, a.attnum`);
const constraints = json(`
  SELECT c.conrelid::regclass::text AS table, c.conname AS name, c.contype AS type, pg_get_constraintdef(c.oid) AS def,
         c.conkey AS cols, c.confrelid::regclass::text AS ref
  FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
  WHERE t.relnamespace = 'public'::regnamespace AND c.contype IN ('p','u','f','c','x')
  ORDER BY c.conrelid::regclass::text, CASE c.contype WHEN 'p' THEN 0 WHEN 'u' THEN 1 WHEN 'x' THEN 2 WHEN 'f' THEN 3 ELSE 4 END, c.conname`);
const indexes = json(`
  SELECT i.tablename AS table, i.indexname AS name, i.indexdef AS def FROM pg_indexes i
  WHERE i.schemaname = 'public' AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conname = i.indexname AND c.contype IN ('p','u','x'))
  ORDER BY i.tablename, i.indexname`);
const sequences = json(`
  SELECT s.sequencename AS name, s.data_type AS type, s.last_value AS last, s.start_value AS start, s.increment_by AS inc,
         dc.relname AS table, a.attname AS column
  FROM pg_sequences s
  JOIN pg_class sc ON sc.relname = s.sequencename AND sc.relnamespace = 'public'::regnamespace
  LEFT JOIN pg_depend dp ON dp.objid = sc.oid AND dp.deptype IN ('a','i') AND dp.refobjsubid > 0
  LEFT JOIN pg_class dc ON dc.oid = dp.refobjid LEFT JOIN pg_attribute a ON a.attrelid = dp.refobjid AND a.attnum = dp.refobjsubid
  WHERE s.schemaname = 'public'`);

const comments = json(`
  SELECT c.relname AS table, a.attname AS column, d.description AS text FROM pg_description d
  JOIN pg_class c ON c.oid = d.objoid AND c.relnamespace = 'public'::regnamespace AND c.relkind = 'r'
  LEFT JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = d.objsubid AND d.objsubid > 0
  WHERE d.classoid = 'pg_class'::regclass ORDER BY 1, d.objsubid`);
const colsOf = (t) => columns.filter((c) => c.table === t);
const consOf = (t) => constraints.filter((c) => c.table === t);

// ---------------------------------------------------------------- historia (comentarios das migrations antigas)
const files = readdirSync(FROM).filter((f) => /^V\d+__.*\.sql$/.test(f))
  .sort((a, b) => Number(a.match(/^V(\d+)/)[1]) - Number(b.match(/^V(\d+)/)[1]));
const created = {}; const history = {}; const notes = {}; const colNotes = {};
const precedingComments = (lines, i) => {
  const out = []; let j = i - 1;
  while (j >= 0 && /^\s*--/.test(lines[j])) { out.unshift(lines[j]); j--; }
  return out.map((l) => l.replace(/^\s*--\s?/, '').replace(/PrimeVest/g, 'Activest')).filter((l) => !/^[-=\s]*$/.test(l));
};
files.forEach((f, order) => {
  const v = f.match(/^(V\d+)/)[1];
  const lines = readFileSync(join(FROM, f), 'utf8').replace(/\r/g, '').split('\n');
  let inCreate = null;
  lines.forEach((line, i) => {
    let m = line.match(/^\s*CREATE TABLE (?:IF NOT EXISTS )?(\w+)/i);
    if (m && tables.includes(m[1])) {
      const t = m[1]; inCreate = t;
      if (!created[t]) {
        created[t] = { v, f, order, pos: i };
        const c = precedingComments(lines, i).filter((l) => l.trim().toLowerCase() !== t);
        if (c.length) (notes[t] ||= []).push({ v, lines: c });
      }
      (history[t] ||= new Set()).add(v);
      return;
    }
    if (inCreate) {
      const cm = line.match(/^\s*(\w+)\s+[A-Za-z].*?--\s?(.*)$/);
      if (cm) cm[2] = cm[2].replace(/PrimeVest/g, 'Activest');
      if (cm && !/^(CONSTRAINT|PRIMARY|UNIQUE|CHECK|FOREIGN)$/i.test(cm[1])) (colNotes[inCreate] ||= {})[cm[1]] ||= `${cm[2].trim()} (${v})`;
      if (/^\s*\);/.test(line)) inCreate = null;
      return;
    }
    m = line.match(/^\s*ALTER TABLE (?:ONLY )?(?:IF EXISTS )?(\w+)/i) || line.match(/^\s*CREATE (?:UNIQUE )?INDEX .* ON (?:ONLY )?(\w+)/i)
      || line.match(/^\s*(?:INSERT INTO|UPDATE|DELETE FROM) (\w+)/i);
    if (m && tables.includes(m[1])) {
      const t = m[1]; (history[t] ||= new Set()).add(v);
      if (/^\s*ALTER TABLE/i.test(line)) {
        const c = precedingComments(lines, i);
        if (c.length) (notes[t] ||= []).push({ v, lines: c });
        const add = line.match(/ADD COLUMN (?:IF NOT EXISTS )?(\w+).*?--\s?(.*)$/i);
        if (add) (colNotes[t] ||= {})[add[1]] ||= `${add[2].trim()} (${v})`;
      }
    }
  });
});
const fileOf = Object.fromEntries(files.map((f) => [f.match(/^(V\d+)/)[1], f]));

// ---------------------------------------------------------------- ordem das tabelas
const deps = Object.fromEntries(tables.map((t) => [t, new Set(consOf(t).filter((c) => c.type === 'f' && c.ref !== t).map((c) => c.ref))]));
const base = [...tables].sort((a, b) => ((created[a]?.order ?? 999) - (created[b]?.order ?? 999)) || ((created[a]?.pos ?? 0) - (created[b]?.pos ?? 0)) || a.localeCompare(b));
const ordered = []; const done = new Set();
while (ordered.length < base.length) {
  const next = base.find((t) => !done.has(t) && [...deps[t]].every((d) => done.has(d)));
  if (!next) throw new Error('Ciclo de chaves estrangeiras: ' + base.filter((t) => !done.has(t)).join(', '));
  ordered.push(next); done.add(next);
}

// ---------------------------------------------------------------- tipos, defaults e constraints legiveis
const TYPE = (t) => t
  .replace(/^character varying/, 'VARCHAR').replace(/^character\(/, 'CHAR(').replace(/^timestamp without time zone/, 'TIMESTAMP')
  .replace(/^timestamp with time zone/, 'TIMESTAMPTZ').replace(/^time without time zone/, 'TIME').replace(/^double precision/, 'DOUBLE PRECISION')
  .replace(/^(integer|bigint|smallint|numeric|boolean|text|date|uuid|jsonb|json|bytea|real)/, (m) => m.toUpperCase());
const cleanCasts = (s) => s.replace(/::character varying(\[\])?/g, (m, arr) => (arr ? '::VARCHAR[]' : '')).replace(/::text\b(?!\[)/g, '')
  .replace(/::numeric\b/g, '').replace(/::bigint\b/g, '').replace(/::integer\b/g, '');
const DEFAULT = (d) => d.replace(/\bnow\(\)/g, 'NOW()').replace(/\bgen_random_uuid\(\)/g, 'gen_random_uuid()')
  .replace(/^'([^']*)'::character varying$/, "'$1'").replace(/^'([^']*)'::text$/, "'$1'").replace(/^'([^']*)'::(numeric|bigint|integer)$/, '$1')
  .replace(/^true$/, 'TRUE').replace(/^false$/, 'FALSE');
const CHECK = (d) => {
  let s = cleanCasts(d);
  // (col = ANY (ARRAY['A', 'B'])) -> col IN ('A', 'B')
  s = s.replace(/\(\(?(\w+)\)? = ANY \(\(ARRAY\[([^\]]*)\]\)::text\[\]\)\)/g, (m, c, list) => `(${c} IN (${list}))`);
  s = s.replace(/\(\(?(\w+)\)? = ANY \(\(?ARRAY\[([^\]]*)\]\)?(?:::VARCHAR\[\])?\)\)/g, (m, c, list) => `(${c} IN (${list}))`);
  s = s.replace(/(?<!\w)\(\((\w+)\) /g, '($1 ').replace(/(?<!\w)\((\w+)\) (>=|<=|=|<>|>|<) /g, '$1 $2 ');
  return s;
};

// Indice parcial: so a lista IN fica legivel; o resto da definicao (casts de uuid etc.) vai como o banco escreve.
const INDEX = (d) => d.replace(/ ON public\./, ' ON ').replace(/ USING btree /, ' ')
  .replace(/\(\((\w+)\)::text = ANY \(\(ARRAY\[([^\]]*)\]\)::text\[\]\)\)/g, (m, c, list) => `(${c} IN (${list.replace(/::character varying/g, '')}))`);

const serialOf = (t, c) => {
  const s = sequences.find((q) => q.table === t && q.column === c.name);
  if (!s || s.name !== `${t}_${c.name}_seq` || c.def !== `nextval('${s.name}'::regclass)` || Number(s.start) !== 1 || Number(s.inc) !== 1) return null;
  if (c.type === 'bigint' && s.type === 'bigint') return 'BIGSERIAL';
  if (c.type === 'integer' && s.type === 'integer') return 'SERIAL';
  if (c.type === 'smallint' && s.type === 'smallint') return 'SMALLSERIAL';
  return null;
};

const CRLF = (s) => s.replace(/\r/g, '').replace(/\n/g, '\r\n');
const wrap = (text, width = 112) => {
  const out = [];
  for (const para of text) {
    if (para.length <= width) { out.push(para); continue; }
    const indent = para.match(/^\s*/)[0]; let line = '';
    for (const w of para.trim().split(/\s+/)) {
      if ((indent + line + ' ' + w).length > width && line) { out.push(indent + line); line = w; } else line = line ? line + ' ' + w : w;
    }
    if (line) out.push(indent + line);
  }
  return out;
};

// ---------------------------------------------------------------- V1__schema.sql
const today = new Date().toISOString().slice(0, 10);
let schema = `-- =============================================================================
-- Activest — schema completo (V1).
--
-- Consolidado em ${today} na renomeacao PrimeVest -> Activest (docs/planos/PLANO_RENOMEACAO_ACTIVEST.md, 4.2), a
-- partir do banco que as migrations V1–V67 do PrimeVest produziam (carga/scripts/migrations/consolidate.mjs).
-- Cada tabela nasce pronta: colunas, defaults, constraints e indices finais, sem ALTER posterior. A historia de
-- cada tabela (as migrations que a criaram e mudaram) esta no repositorio primevest; cada bloco abaixo cita as
-- versoes de origem.
--
-- A ordem das tabelas e a das chaves estrangeiras. Colunas: chave primaria, dados, chaves estrangeiras e
-- auditoria no fim.
--
-- Convencoes (herdadas):
--   - UUID nas tabelas de dados do usuario; BIGSERIAL no catalogo.
--   - Quantidades e precos unitarios em NUMERIC(18,8) (cripto); valores monetarios agregados em NUMERIC(18,2).
--   - Toda coluna de data de auditoria e NOT NULL DEFAULT NOW(): o preenchimento no Java e manual, e um INSERT
--     por SQL direto (seed, migration, psql) depende do default.
--   - Campo novo obrigatorio em tabela existente (migrations futuras) nasce com o default informado (REGRAS 8.1).
-- =============================================================================
`;
for (const t of ordered) {
  const cols = colsOf(t); const cons = consOf(t);
  const pk = cons.find((c) => c.type === 'p'); const pkCols = pk ? pk.cols : [];
  const fkCols = new Set(cons.filter((c) => c.type === 'f').flatMap((c) => c.cols));
  const sorted = [
    ...cols.filter((c) => pkCols.includes(c.num)),
    ...cols.filter((c) => !pkCols.includes(c.num) && !fkCols.has(c.num) && !AUDIT.includes(c.name)),
    ...cols.filter((c) => !pkCols.includes(c.num) && fkCols.has(c.num) && !AUDIT.includes(c.name)),
    ...AUDIT.map((a) => cols.find((c) => c.name === a && !pkCols.includes(c.num))).filter(Boolean),
  ];
  const hist = [...(history[t] || [])].sort((a, b) => Number(a.slice(1)) - Number(b.slice(1)));
  schema += `\n\n-- -----------------------------------------------------------------------------\n-- ${t}\n-- -----------------------------------------------------------------------------\n`;
  schema += `-- Origem: ${hist.map((v) => fileOf[v]?.replace(/\.sql$/, '') ?? v).join(', ')}\n`;
  for (const n of notes[t] || []) {
    schema += `--\n` + wrap(n.lines).map((l, i) => `-- ${i === 0 ? `[${n.v}] ` : ''}${l}`.trimEnd()).join('\n') + '\n';
  }
  const w = Math.max(...sorted.map((c) => c.name.length));
  const defs = sorted.map((c) => {
    const serial = serialOf(t, c);
    let s = `    ${c.name.padEnd(w)} ${serial ?? TYPE(c.type)}`;
    if (c.notnull && !(serial && pkCols.includes(c.num))) s += ' NOT NULL';
    if (c.def && !serial) s += ` DEFAULT ${DEFAULT(c.def)}`;
    return { s, note: colNotes[t]?.[c.name] };
  });
  const cdefs = cons.map((c) => ({ s: `    CONSTRAINT ${c.name} ${c.type === 'c' ? CHECK(c.def) : cleanCasts(c.def)}` }));
  const all = [...defs, ...cdefs];
  schema += `CREATE TABLE ${t} (\n` + all.map((d, i) => d.s + (i < all.length - 1 ? ',' : '') + (d.note ? `  -- ${d.note}` : '')).join('\n') + '\n);\n';
  for (const c of comments.filter((x) => x.table === t)) {
    schema += `${c.column ? `COMMENT ON COLUMN ${t}.${c.column}` : `COMMENT ON TABLE ${t}`} IS '${c.text.replace(/'/g, "''")}';\n`;
  }
  const idx = indexes.filter((i) => i.table === t);
  if (idx.length) schema += '\n' + idx.map((i) => INDEX(i.def) + ';').join('\n') + '\n';
  for (const s of sequences.filter((q) => q.table === t)) {
    const c = cols.find((x) => x.name === s.column);
    if (!serialOf(t, c)) throw new Error(`Sequencia fora do padrao BIGSERIAL/SERIAL: ${s.name}`);
  }
}
const orphanSeq = sequences.filter((s) => !s.table);
if (orphanSeq.length) throw new Error('Sequencias sem dono: ' + orphanSeq.map((s) => s.name).join(', '));
mkdirSync(OUT, { recursive: true });
writeFileSync(join(OUT, 'V1__schema.sql'), CRLF(schema), 'utf8');

// ---------------------------------------------------------------- seeds
const counts = Object.fromEntries(json(tables.map((t) => `SELECT '${t}' AS t, count(*) AS n FROM ${t}`).join(' UNION ALL ')).map((r) => [r.t, Number(r.n)]));
const seeded = tables.filter((t) => counts[t] > 0);
const mapped = SEEDS.flatMap((s) => s[2]);
const missing = seeded.filter((t) => !mapped.includes(t));
if (missing.length) throw new Error('Tabelas com dados fora do mapa de seeds: ' + missing.join(', '));
const pos = Object.fromEntries(ordered.map((t, i) => [t, i]));
for (const [file, desc, list] of SEEDS) {
  const ts = list.filter((t) => counts[t] > 0).sort((a, b) => pos[a] - pos[b]);
  let sql = `-- ${desc}\n--\n-- Consolidado em ${today} (docs/planos/PLANO_RENOMEACAO_ACTIVEST.md, 4.2): o estado final que as migrations V1–V67\n`
    + `-- do PrimeVest deixavam, com ids explicitos e as sequencias ajustadas no fim. Datas de auditoria ficam com o default.\n`;
  for (const t of ts) {
    const cons = consOf(t); const pk = cons.find((c) => c.type === 'p');
    const cols = colsOf(t).filter((c) => !AUDIT.includes(c.name) || !(c.def || '').includes('now()')).filter((c) => !(OMIT[t] || []).includes(c.name));
    const pkNames = pk ? colsOf(t).filter((c) => pk.cols.includes(c.num)).map((c) => c.name) : cols.map((c) => c.name);
    const lit = (c) => (['N', 'B'].includes(c.cat) ? `COALESCE(${c.name}::text, 'NULL')` : `quote_nullable(${c.name}::text)`);
    const rows = psql(`SELECT '(' || concat_ws(', ', ${cols.map(lit).join(', ')}) || ')' FROM ${t} ORDER BY ${pkNames.join(', ')};`)
      .replace(/\r/g, '').split('\n').filter(Boolean);
    sql += `\n\n-- ${t} (${rows.length} linhas; origem: ${[...(history[t] || [])].filter((v) => /INSERT|UPDATE|DELETE/i.test(readFileSync(join(FROM, fileOf[v]), 'utf8').match(new RegExp(`(INSERT INTO|UPDATE|DELETE FROM)\\s+${t}\\b`, 'i'))?.[0] ?? '')).join(', ')})\n`;
    for (let i = 0; i < rows.length; i += 1000) {
      sql += `INSERT INTO ${t} (${cols.map((c) => c.name).join(', ')}) VALUES\n` + rows.slice(i, i + 1000).map((r) => '    ' + r).join(',\n') + ';\n';
    }
    for (const s of sequences.filter((q) => q.table === t && q.last !== null)) {
      sql += `SELECT setval('${s.name}', ${s.last});\n`;
    }
  }
  writeFileSync(join(OUT, file), CRLF(sql), 'utf8');
  console.log(file, ts.map((t) => `${t}=${counts[t]}`).join(' '));
}
console.log('V1__schema.sql', ordered.length, 'tabelas');
