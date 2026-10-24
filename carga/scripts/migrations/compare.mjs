// Compara dois bancos PostgreSQL em conteineres: estrutura (pg_dump --schema-only, sem a ordem das colunas e com
// `col IN (...)` igual a `col = ANY (ARRAY[...])`), conteudo de cada tabela (linhas ordenadas, sem created_at,
// updated_at e o id aleatorio de strategy_reservations) e o valor das sequencias.
// A prova da consolidacao das migrations (docs/planos/PLANO_RENOMEACAO_ACTIVEST.md, 4.2).
//
// Uso: node carga/scripts/migrations/compare.mjs <conteiner>/<banco> <conteiner>/<banco>
// Sai com 1 se houver diferenca.
import { execFileSync } from 'node:child_process';

const [A, B] = process.argv.slice(2);
if (!A || !B) { console.error('Uso: compare.mjs <conteiner>/<banco> <conteiner>/<banco>'); process.exit(2); }

const run = (spec, cmd, input) => {
  const [c, d] = spec.split('/');
  return execFileSync('docker', ['exec', '-i', c, ...cmd(d)], { input, maxBuffer: 1024 * 1024 * 1024, encoding: 'utf8' }).replace(/\r/g, '');
};
const psql = (spec, sql) => run(spec, (d) => ['psql', '-U', 'postgres', '-d', d, '-At', '-F', ';', '-v', 'ON_ERROR_STOP=1'], sql);

const canon = (l) => l.replace(/ANY \(\(ARRAY\[([^\]]*)\]\)::text\[\]\)/g, (m, list) => `ANY (ARRAY[${list.replace(/::character varying/g, '::text')}])`);
function schema(spec) {
  const dump = run(spec, (d) => ['pg_dump', '-U', 'postgres', '-d', d, '--schema-only', '--no-owner', '--no-privileges', '-T', 'flyway_schema_history'])
    .split('\n').filter((l) => !/^\\(un)?restrict/.test(l) && !l.startsWith('--') && l.trim()).map(canon);
  const out = []; let block = null;
  for (const l of dump) {
    if (block) {
      if (l.startsWith(');')) { out.push(block.map((x) => x.replace(/,$/, '')).sort().join('\n') + '\n);'); block = null; } else block.push(l);
    } else if (l.startsWith('CREATE TABLE')) { out.push(l); block = []; } else out.push(l);
  }
  return out.join('\n').split(';').map((s) => s.trim()).filter(Boolean).sort();
}
function data(spec) {
  const q = `select string_agg(format('select %L, md5(coalesce(string_agg(r, E''\\n'' order by r), '''')), count(*) from (select (%s)::text as r from %I x) y',
      c.relname,
      (select string_agg(format('x.%I', a.attname), ', ' order by a.attname) from pg_attribute a
        where a.attrelid = c.oid and a.attnum > 0 and not a.attisdropped and a.attname not in ('created_at', 'updated_at')
          and not (c.relname = 'strategy_reservations' and a.attname = 'id')),
      c.relname), ' union all ' order by c.relname)
    from pg_class c where c.relkind = 'r' and c.relnamespace = 'public'::regnamespace and c.relname <> 'flyway_schema_history'`;
  return psql(spec, psql(spec, q).trim() + ' order by 1').split('\n').filter(Boolean);
}
const seqs = (spec) => psql(spec, "select sequencename, last_value from pg_sequences where schemaname = 'public' order by 1").split('\n').filter(Boolean);

let diffs = 0;
const sa = schema(A); const sb = schema(B);
const onlyA = sa.filter((s) => !sb.includes(s)); const onlyB = sb.filter((s) => !sa.includes(s));
console.log(`estrutura: ${onlyA.length + onlyB.length ? 'DIFERENTE' : 'igual'} (${sa.length} objetos)`);
onlyA.forEach((s) => console.log(`  so em ${A}:\n    ${s.replace(/\n/g, '\n    ')}`)); onlyB.forEach((s) => console.log(`  so em ${B}:\n    ${s.replace(/\n/g, '\n    ')}`));
diffs += onlyA.length + onlyB.length;
const da = data(A); const db = data(B);
const bad = da.filter((l) => !db.includes(l));
console.log(`dados: ${bad.length ? 'DIFERENTES' : 'iguais'} (${da.length} tabelas)`);
bad.forEach((l) => console.log(`  ${l}  x  ${db.find((x) => x.split(';')[0] === l.split(';')[0]) ?? '(ausente)'}`));
diffs += bad.length;
const qa = seqs(A); const qb = seqs(B); const sq = qa.filter((l) => !qb.includes(l));
console.log(`sequencias: ${sq.length ? 'DIFERENTES' : 'iguais'} (${qa.length})`);
sq.forEach((l) => console.log(`  ${l}  x  ${qb.find((x) => x.split(';')[0] === l.split(';')[0]) ?? '(ausente)'}`));
diffs += sq.length;
process.exit(diffs ? 1 : 0);
