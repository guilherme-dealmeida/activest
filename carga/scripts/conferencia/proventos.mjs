// Conferencia independente do catalogo de proventos (docs/planos/PLANO_CORRECAO_PROVENTOS.md, C4 e C9).
//
// Le o banco (psql do conteiner) e os arquivos de origem em carga/data (avisos do FNET, situacao de cada aviso e,
// com --cotahist, o ISIN de cada codigo negociado no COTAHIST) e procura, com implementacao propria, as classes de
// erro achadas em 20–21/10/2026 (docs/planos/PLANO_INVESTIGACAO_PROVENTOS_DUPLICADOS.md):
//   A  parcelas da B3 recente duplicadas (mesmo ativo, tipo, data-com, parcela e pagamento; valor diferindo so no
//      arredondamento)
//   B  provento de recibo de subscricao/direito na cota (ISIN com "R" no 7o caractere, ticker do aviso que nao e do fundo)
//   C  evento de aviso que o FNET marcou como inativo ou cancelado (carga/data/fnet/status)
//   D  reserva da B3 de FII nao absorvida: o FNET tem o mesmo provento (pagamento e valor) com outra data-base
//   E  ISIN de cota de outro fundo: classificado em "ticker do fundo" (vale o ticker), "outro fundo do catalogo"
//      (erro) e "ISIN antigo do proprio fundo" (renomeacao; o aviso e sempre do CNPJ do fundo)
// Saida: resumo por classe e, com --detalhe, cada evento. Codigo de saida 1 se A, B, C, D ou E-erro > 0.
//
// Uso: node carga/scripts/conferencia/proventos.mjs [--detalhe] [--cotahist] [--sql] [--db activest-db] [--data carga/data]
//      --sql imprime a chave natural de cada evento indevido, no formato da lista da migration de limpeza
import { execFileSync, spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { ROOT, option } from '../lib/env.mjs';

const args = process.argv.slice(2);
const DB = option(args, '--db', 'activest-db');
const DATA = option(args, '--data', join(ROOT, 'carga', 'data'));
const DETALHE = args.includes('--detalhe');
const COTAHIST = args.includes('--cotahist');
const SQL = args.includes('--sql');
const TOL = 0.000001;

const sql = (q) => execFileSync('docker', ['exec', DB, 'psql', '-U', 'postgres', '-d', 'db_activest', '-At', '-F', '\t', '-c', q],
  { encoding: 'utf8', maxBuffer: 512 * 1024 * 1024 })
  .split('\n').filter(Boolean).map(l => l.split('\t'));

// ------------------------------------------------------------------ dados
const assets = sql(`SELECT a.id, a.ticker, coalesce(a.isin,''), coalesce(a.issuer_id::text,''), t.name
  FROM assets a JOIN asset_types t ON t.id = a.asset_type_id`)
  .map(([id, ticker, isin, issuer, tipo]) => ({ id, ticker, isin, issuer, tipo }));
const assetById = new Map(assets.map(a => [a.id, a]));
const assetsByIsin = new Map();
assets.filter(a => a.isin).forEach(a => assetsByIsin.set(a.isin, [...(assetsByIsin.get(a.isin) ?? []), a]));
const assetsByIssuer = new Map();
assets.filter(a => a.issuer).forEach(a => assetsByIssuer.set(a.issuer, [...(assetsByIssuer.get(a.issuer) ?? []), a]));
const cnpjByIssuer = new Map(sql(`SELECT id, coalesce(cnpj,'') FROM issuers`));

const eventos = sql(`SELECT id, asset_id, coalesce(type,''), data_com, coalesce(payment_date::text,''), value_per_share, seq, source,
  coalesce(source_ref,''), coalesce(isin,'') FROM asset_cash_events`)
  .map(([id, asset, type, dataCom, pag, valor, seq, source, ref, isin]) =>
    ({ id, asset, type, dataCom, pag, valor: Number(valor), valorTxt: valor, seq, source, ref, isin }));
const dividendos = new Map(sql(`SELECT cash_event_id, count(*) FROM dividends WHERE cash_event_id IS NOT NULL GROUP BY 1`));

const base = (t) => (t.length > 5 && t.endsWith('F') && /\d/.test(t.at(-2)) ? t.slice(0, -1) : t);
const tickerBase = (t) => { const m = (t ?? '').trim().toUpperCase().match(/^([A-Z]{4})(\d{2})[A-Z]?$/); return m ? m[1] + m[2] : (t ?? '').trim().toUpperCase(); };

// avisos do FNET: ticker e ISIN de cada provento, pelo id do documento
const FNET = join(DATA, 'fnet');
const avisoCache = new Map();
function aviso(ref) {
  if (avisoCache.has(ref)) return avisoCache.get(ref);
  const f = join(FNET, `${ref}.xml`);
  let itens = [];
  if (existsSync(f)) {
    const xml = readFileSync(f, 'latin1');
    // formato antigo (2016 a ~2019): ISIN e ticker da cota em DadosGerais (CodISINCota, CodNegociacaoCota)
    const geral = xml.match(/<CodNegociacao(?:Cota)?>([^<]*)<\/CodNegociacao(?:Cota)?>/);
    for (const bloco of xml.split(/<Provento>/).slice(1)) {
      itens.push({ isin: (bloco.match(/<CodISIN>([^<]*)</) ?? [])[1]?.trim(), ticker: (bloco.match(/<CodNegociacao>([^<]*)</) ?? [])[1]?.trim(),
        valores: [...bloco.matchAll(/<ValorProvento>([^<]*)</g)].map(m => Number(m[1])) });
    }
    if (!itens.length) itens.push({ isin: (xml.match(/<CodISIN(?:Cota)?>([^<]*)</) ?? [])[1]?.trim(), ticker: geral?.[1]?.trim() });
  }
  avisoCache.set(ref, itens);
  return itens;
}
// situacao de cada aviso no FNET
const inativo = new Set();
const STATUS = join(FNET, 'status');
let comSituacao = 0;
if (existsSync(STATUS)) {
  for (const f of readdirSync(STATUS).filter(n => n.endsWith('.json'))) {
    comSituacao++;
    for (const s of JSON.parse(readFileSync(join(STATUS, f), 'utf8'))) if (s.status && s.status !== 'AC') inativo.add(String(s.id));
  }
}
// COTAHIST: ISIN -> codigos negociados (opcional; tar do sistema extrai o zip para a saida padrao)
const negociado = new Map();
async function cotahist(isins) {
  const dir = join(DATA, 'b3', 'cotahist');
  for (const f of readdirSync(dir).filter(n => /^COTAHIST_A\d{4}\.ZIP$/i.test(n)).sort()) {
    const tar = spawn('tar', ['-xOf', join(dir, f)]);
    tar.stdout.setEncoding('latin1');
    for await (const l of createInterface({ input: tar.stdout, crlfDelay: Infinity })) {
      const isin = l.slice(230, 242);
      if (!isins.has(isin)) continue;
      const cod = l.slice(12, 24).trim();
      const d = `${l.slice(2, 6)}-${l.slice(6, 8)}-${l.slice(8, 10)}`;
      const s = negociado.get(isin) ?? new Map();
      const [ini, fim] = s.get(cod) ?? [d, d];
      s.set(cod, [ini < d ? ini : d, fim > d ? fim : d]);
      negociado.set(isin, s);
    }
  }
}

// ------------------------------------------------------------------ classes
const achados = { A: [], B: [], C: [], D: [], E_ticker: [], E_outro: [], E_antigo: [] };
// eventos indevidos (A, B, C, D, E-erro): a chave natural de cada um vira a lista da migration de limpeza
const indevidos = new Map();
const marca = (classe, e) => indevidos.set(e.id, { classe, e });
const desc = (e, extra = '') => {
  const a = assetById.get(e.asset);
  return `${a.ticker} ev ${e.id} ${e.type} com=${e.dataCom} pag=${e.pag || '-'} v=${e.valorTxt} s${e.seq} ${e.source}${e.ref ? ' ' + e.ref : ''}`
    + `${e.isin ? ' isin=' + e.isin : ''}${dividendos.has(e.id) ? ' [' + dividendos.get(e.id) + ' provento(s)]' : ''}${extra}`;
};

// A
// so a B3 recente: no historico longo (B3_HISTORICO) as parcelas vem sem pagamento e diferem no arredondamento de
// proposito (ALLD3 0,482726014/015, AZEV3 11 x ...041 e 1 x ...042) — e o que a B3 publica, conferido nos arquivos
const grupoA = new Map();
for (const e of eventos.filter(x => x.source === 'B3')) {
  const k = [e.asset, e.type, e.dataCom, e.seq, e.pag].join('|');
  grupoA.set(k, [...(grupoA.get(k) ?? []), e]);
}
for (const g of grupoA.values()) {
  if (g.length < 2) continue;
  g.sort((x, y) => Number(x.id) - Number(y.id));
  for (const e of g.slice(1)) if (Math.abs(e.valor - g[0].valor) <= TOL && e.valorTxt !== g[0].valorTxt) { achados.A.push(desc(e, ` (igual a ${g[0].id})`)); marca('A', e); }
}
// A: parcela da B3 ao lado do mesmo provento do FNET (mesma data-com, valor e pagamento) — o FNET completa a B3; os dois
// juntos sao o mesmo provento contado duas vezes (RTEL16 02/04/2026: B3 parcela 2 e FNET parcela 1, R$ 0,45)
const fnetChave = new Map();
for (const e of eventos.filter(x => x.source === 'FNET')) {
  const k = [e.asset, e.type, e.dataCom, e.pag].join('|');
  fnetChave.set(k, [...(fnetChave.get(k) ?? []), e]);
}
for (const e of eventos.filter(x => x.source === 'B3' || x.source === 'B3_HISTORICO')) {
  const par = (fnetChave.get([e.asset, e.type, e.dataCom, e.pag].join('|')) ?? []).find(f => Math.abs(f.valor - e.valor) <= TOL);
  if (par) { achados.A.push(desc(e, ` (mesmo provento do FNET ${par.id})`)); marca('A', e); }
}
// B, C, E (eventos do FNET)
for (const e of eventos.filter(x => x.source === 'FNET')) {
  const a = assetById.get(e.asset);
  if (inativo.has(e.ref)) { achados.C.push(desc(e, ' — aviso inativo/cancelado no FNET')); marca('C', e); }
  if (e.isin && e.isin === a.isin) {
    // R13: no aviso com a linha do ticker do fundo, o valor so aparece na linha de outra classe (URPR13 de URPR11),
    // com o ISIN da cota
    const grupoT = new Set((a.issuer ? assetsByIssuer.get(a.issuer) : [a]).map(x => base(x.ticker)));
    const linhas = aviso(e.ref).filter(i => i.ticker && i.valores?.some(v => Math.abs(v - e.valor) <= TOL));
    const temCota = aviso(e.ref).some(i => i.ticker && grupoT.has(tickerBase(i.ticker)));
    const raiz = a.ticker.slice(0, 4);
    if (temCota && linhas.length && linhas.every(i => !grupoT.has(tickerBase(i.ticker)) && tickerBase(i.ticker).startsWith(raiz))) {
      achados.B.push(desc(e, ` linha de ${linhas.map(i => i.ticker).join(',')} no aviso da cota`)); marca('B', e);
    }
    continue;
  }
  if (!e.isin) continue;
  const grupo = a.issuer ? assetsByIssuer.get(a.issuer) : [a];
  const tickersGrupo = new Set(grupo.map(x => base(x.ticker)));
  const item = aviso(e.ref).find(i => i.isin === e.isin) ?? aviso(e.ref)[0] ?? {};
  const doFundo = item.ticker && tickersGrupo.has(tickerBase(item.ticker));
  // o ISIN de recibo vence o ticker (o administrador repete o ticker da cota nas linhas dos recibos)
  if (/^BR[A-Z0-9]{4}[RD]/.test(e.isin)) { achados.B.push(desc(e, ` ticker do aviso ${item.ticker ?? '-'}`)); marca('B', e); continue; }
  // no dia em que o fundo tem a linha da propria cota (ISIN do ativo), linha de outro ISIN e de outro papel, mesmo com
  // o ticker do fundo (R12: EURO11 19/04/2017, recibo BREURO01M15 com o ticker EURO11)
  const diaDaCota = e.pag && eventos.some(o => o.asset === e.asset && o.pag === e.pag && o.isin && o.isin === a.isin);
  if (diaDaCota) { achados.E_outro.push(desc(e, ` outro papel no dia do rendimento da cota; ticker do aviso ${item.ticker ?? '-'}`)); marca('E', e); continue; }
  if (doFundo) { achados.E_ticker.push(desc(e, ` ticker do aviso ${item.ticker}`)); continue; }
  const outro = (assetsByIsin.get(e.isin) ?? []).find(o => o.issuer && o.issuer !== a.issuer);
  if (outro) { achados.E_outro.push(desc(e, ` ISIN de ${outro.ticker} (emissor ${cnpjByIssuer.get(outro.issuer)}) ticker do aviso ${item.ticker ?? '-'}`)); marca('E', e); continue; }
  achados.E_antigo.push({ e, texto: desc(e, ` ticker do aviso ${item.ticker ?? '-'}`) });
}
// C sem arquivo de situacao do fundo: dois avisos com o mesmo pagamento e valor e data-base ate 7 dias — o mais novo
// e a retificacao (onde ha situacao, o antigo esta inativo/cancelado em 7 de 8 pares)
const comArquivo = new Set(existsSync(STATUS) ? readdirSync(STATUS).filter(n => n.endsWith('.json')).map(n => n.slice(0, -5)) : []);
const fnetGrupo = new Map();
for (const e of eventos.filter(x => x.source === 'FNET' && x.pag && /^\d+$/.test(x.ref))) {
  const k = [e.asset, e.type, e.pag].join('|');
  fnetGrupo.set(k, [...(fnetGrupo.get(k) ?? []), e]);
}
for (const g of fnetGrupo.values()) {
  if (g.length < 2) continue;
  for (const e of g) {
    const a = assetById.get(e.asset);
    if (comArquivo.has(cnpjByIssuer.get(a.issuer)) || indevidos.has(e.id)) continue;
    const novo = g.find(o => o !== e && Number(o.ref) > Number(e.ref) && Math.abs(o.valor - e.valor) <= TOL
      && o.dataCom !== e.dataCom && Math.abs(new Date(o.dataCom) - new Date(e.dataCom)) <= 7 * 864e5);
    if (novo) { achados.C.push(desc(e, ` — retificado pelo aviso ${novo.ref} (sem arquivo de situacao)`)); marca('C', e); }
  }
}
// D
const fnetPag = new Map();
for (const e of eventos.filter(x => x.source === 'FNET' && x.pag)) {
  const k = [e.asset, e.type, e.pag].join('|');
  fnetPag.set(k, [...(fnetPag.get(k) ?? []), e]);
}
for (const e of eventos.filter(x => x.source === 'B3' && x.pag && assetById.get(x.asset).tipo === 'FII')) {
  const par = (fnetPag.get([e.asset, e.type, e.pag].join('|')) ?? []).find(f => Math.abs(f.valor - e.valor) <= TOL && f.dataCom !== e.dataCom);
  if (par) { achados.D.push(desc(e, ` (FNET ${par.id} com=${par.dataCom})`)); marca('D', e); }
}
if (COTAHIST && achados.E_antigo.length) {
  await cotahist(new Set(achados.E_antigo.map(x => x.e.isin)));
}
achados.E_antigo = achados.E_antigo.map(({ e, texto }) => {
  const s = negociado.get(e.isin);
  return texto + (COTAHIST ? (s ? ' cotahist: ' + [...s].map(([c, [i, f]]) => `${c} ${i}..${f}`).join(', ') : ' cotahist: nunca negociado') : '');
});

// ------------------------------------------------------------------ saida
const nomes = {
  A: 'A  parcelas da B3 duplicadas', B: 'B  recibo/direito na cota', C: 'C  aviso inativo/cancelado no FNET',
  D: 'D  reserva da B3 nao absorvida pelo FNET', E_outro: 'E  ISIN de outro fundo do catalogo (erro)',
  E_ticker: 'E  ISIN de outro fundo, ticker do fundo (ok: vale o ticker)', E_antigo: 'E  ISIN antigo do proprio fundo (ok: renomeacao)',
};
console.log(`Conferencia de proventos — ${eventos.length} eventos; situacao do FNET de ${comSituacao} fundos (${inativo.size} avisos inativos/cancelados)`);
for (const [k, nome] of Object.entries(nomes)) {
  console.log(`${nome.padEnd(62)} ${String(achados[k].length).padStart(6)}`);
  if (DETALHE) achados[k].forEach(l => console.log('    ' + l));
}
if (SQL) {
  // linhas para a migration: (classe, ticker, tipo, data-com, valor, parcela, fonte, referencia)
  const q = (v) => (v === '' || v == null ? 'NULL' : `'${String(v).replace(/'/g, "''")}'`);
  const linhas = [...indevidos.values()]
    .sort((x, y) => (x.classe + assetById.get(x.e.asset).ticker + x.e.dataCom).localeCompare(y.classe + assetById.get(y.e.asset).ticker + y.e.dataCom))
    .map(({ classe, e }) => `    (${q(classe)}, ${q(assetById.get(e.asset).ticker)}, ${q(e.type)}, DATE ${q(e.dataCom)}, ${e.valorTxt}, ${e.seq}, ${q(e.source)}, ${q(e.ref)})`);
  console.log(linhas.join(',\n'));
}
const erros = ['A', 'B', 'C', 'D', 'E_outro'].reduce((s, k) => s + achados[k].length, 0);
process.exit(erros ? 1 : 0);
