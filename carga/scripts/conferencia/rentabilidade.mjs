// Conferencia independente da Rentabilidade (docs/planos/PLANO_RENTABILIDADE.md, L18 e secao 8).
//
// Recalcula, direto das tabelas e com uma implementacao propria (nada do codigo Java), a rentabilidade (TWR) de cada
// classe de ativos com preco de mercado, o CDI e o IPCA do mesmo periodo, o % do CDI e a rentabilidade real — e
// compara com GET /api/v1/performance/summary?type=<classe>&basis=GROSS&range=MAX. Renda fixa privada fica de fora
// (o valor dela e a curva contratada; reconstrui-la aqui seria copiar a regra, nao conferi-la).
//
// Uso: node carga/scripts/conferencia/rentabilidade.mjs [--tipos ACAO,FII,ETF,TESOURO_DIRETO,CRIPTO] [--tol 0.01]
//      [--db primevest-db] [--email ...] [--api URL]
// Le o banco pelo psql do conteiner (docker exec) e a API com a PRIMEVEST_PASSWORD do .env.
import { execFileSync } from 'node:child_process';
import { apiUrls, loadEnv, option } from '../lib/env.mjs';

const args = process.argv.slice(2);
const ENV = loadEnv();
const { api: API } = apiUrls(ENV, args);
const EMAIL = option(args, '--email', ENV.PRIMEVEST_EMAIL || 'guilhermeunatec@gmail.com');
const DB = option(args, '--db', 'primevest-db');
const TIPOS = option(args, '--tipos', 'ACAO,FII,ETF,TESOURO_DIRETO,CRIPTO').split(',');
const TOL = Number(option(args, '--tol', '0.01')); // pontos percentuais

const sql = (q) => execFileSync('docker', ['exec', DB, 'psql', '-U', 'postgres', '-d', 'db_primevest', '-At', '-F', '\t', '-c', q],
  { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 })
  .split('\n').filter(Boolean).map(l => l.split('\t'));

const hoje = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());
const addDays = (d, n) => { const x = new Date(d + 'T12:00:00Z'); x.setUTCDate(x.getUTCDate() + n); return x.toISOString().slice(0, 10); };
const weekday = (d) => { const w = new Date(d + 'T12:00:00Z').getUTCDay(); return w !== 0 && w !== 6; };
const r2 = (v) => Math.round(v * 100) / 100;

// ------------------------------------------------------------------ dados
const [[portfolioId]] = sql(`SELECT p.id FROM portfolios p JOIN users u ON u.id = p.user_id WHERE lower(u.email) = lower('${EMAIL}') LIMIT 1`);
const pregoes = new Set(sql(`SELECT v.reference_date FROM market_index_values v JOIN market_indices i ON i.id = v.index_id WHERE i.code = 'IBOV'`).map(r => r[0]));
const ultimoPregao = [...pregoes].sort().at(-1);
const cdi = new Map(sql(`SELECT v.reference_date, v.value FROM market_index_values v JOIN market_indices i ON i.id = v.index_id WHERE i.code = 'CDI'`).map(r => [r[0], Number(r[1])]));
const ipca = new Map(sql(`SELECT to_char(v.reference_date, 'YYYY-MM'), v.value FROM market_index_values v JOIN market_indices i ON i.id = v.index_id WHERE i.code = 'IPCA'`).map(r => [r[0], Number(r[1])]));
const diasUteis = [...cdi.keys()].sort(); // o CDI sai em todo dia util
const duMes = new Map();
diasUteis.forEach(d => duMes.set(d.slice(0, 7), (duMes.get(d.slice(0, 7)) ?? 0) + 1));

async function token() {
  const r = await fetch(`${API}/api/v1/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: EMAIL, password: ENV.PRIMEVEST_PASSWORD }) });
  if (!r.ok) throw new Error(`login ${r.status}`);
  return (await r.json()).accessToken;
}

function conferir(tipo) {
  const pos = sql(`SELECT pa.id, a.id, a.ticker, coalesce(a.current_price, 0) FROM portfolio_assets pa JOIN assets a ON a.id = pa.asset_id
    JOIN asset_types t ON t.id = a.asset_type_id WHERE pa.portfolio_id = '${portfolioId}' AND t.name = '${tipo}'`)
    .map(([id, assetId, ticker, cur]) => ({ id, assetId, ticker, cur: Number(cur) }));
  if (!pos.length) return null;
  const ids = pos.map(p => `'${p.id}'`).join(',');
  const txs = sql(`SELECT portfolio_asset_id, transaction_date, type, coalesce(shares, 0), coalesce(total_amount, 0) FROM transactions
    WHERE portfolio_asset_id IN (${ids}) AND transaction_date <= '${hoje}' ORDER BY transaction_date, created_at`)
    .map(([p, d, t, s, v]) => ({ p, d, t, s: Number(s), v: Number(v) }));
  if (!txs.length) return null;
  // preco: o fracionario (PETR4F) usa o lote padrao
  const precoDe = new Map();
  for (const p of pos) {
    let assetId = p.assetId;
    if (/\dF$/.test(p.ticker) && p.ticker.length > 5) {
      const base = sql(`SELECT id FROM assets WHERE upper(ticker) = '${p.ticker.slice(0, -1)}'`);
      if (base.length) assetId = base[0][0];
    }
    const serie = new Map(sql(`SELECT reference_date, close_price FROM asset_price_history WHERE asset_id = ${assetId} AND close_price > 0`)
      .map(r => [r[0], Number(r[1])]));
    precoDe.set(p.id, { serie, datas: [...serie.keys()].sort() });
  }
  const eventos = new Map();
  for (const p of pos) {
    eventos.set(p.id, sql(`SELECT data_com, type, factor FROM asset_stock_events WHERE asset_id = ${p.assetId} ORDER BY data_com`)
      .map(([d, t, f]) => ({ d, t, f: Number(f) })));
  }
  const prov = sql(`SELECT portfolio_asset_id, ex_date, total_amount FROM dividends WHERE portfolio_asset_id IN (${ids}) AND ex_date IS NOT NULL`)
    .map(([p, d, v]) => ({ p, d, v: Number(v) }));

  // eixo: pregoes da B3 do primeiro dia ate hoje (depois do ultimo Ibovespa, dia de semana); hoje sempre
  const inicio = txs[0].d;
  const eixo = [];
  for (let d = inicio; d <= hoje; d = addDays(d, 1)) {
    if ((d <= ultimoPregao ? pregoes.has(d) : weekday(d)) || d === hoje) eixo.push(d);
  }
  const noEixo = (d) => eixo.findIndex(x => x >= d);

  const n = eixo.length;
  const V = new Array(n).fill(0), A = new Array(n).fill(0), W = new Array(n).fill(0), D = new Array(n).fill(0);
  let velho = 0;
  for (const p of pos) {
    const minhas = txs.filter(t => t.p === p.id);
    const ev = eventos.get(p.id);
    const { serie, datas } = precoDe.get(p.id);
    for (const t of minhas) {
      const i = noEixo(t.d);
      if (i < 0) continue;
      if (t.t === 'BUY') A[i] += t.v; else W[i] += t.v;
    }
    for (let i = 0; i < n; i++) {
      const d = eixo[i];
      // cotas ao fim do dia: transacoes ate o dia e eventos com data-com anterior, na ordem
      let cotas = 0;
      let k = 0;
      for (const t of minhas) {
        if (t.d > d) break;
        while (k < ev.length && ev[k].d < t.d) { cotas = evento(cotas, ev[k]); k++; }
        cotas = t.t === 'BUY' ? cotas + t.s : Math.max(0, cotas - t.s);
      }
      while (k < ev.length && ev[k].d < d) { cotas = evento(cotas, ev[k]); k++; }
      if (cotas <= 0) continue;
      let preco;
      if (d === hoje && p.cur > 0) preco = p.cur;
      else {
        const j = ultimoAte(datas, d);
        if (j < 0) throw new Error(`${p.ticker} sem preco em ${d}`);
        if (datas[j] !== d && eixo.slice(0, i + 1).filter(x => x > datas[j]).length > 5) velho++;
        preco = serie.get(datas[j]);
      }
      V[i] += r2(cotas * preco);
    }
  }
  for (const pr of prov) {
    let ex = addDays(pr.d, 1);
    while (!(ex <= ultimoPregao ? pregoes.has(ex) : weekday(ex))) ex = addDays(ex, 1);
    if (ex > hoje) continue;
    const i = noEixo(ex);
    if (i >= 0) D[i] += pr.v;
  }
  // TWR: compra no comeco do dia, venda e provento no fim
  let f = 1;
  const acum = [];
  for (let i = 0; i < n; i++) {
    const base = (i > 0 ? V[i - 1] : 0) + A[i];
    if (base > 0.005) f *= (V[i] + W[i] + D[i]) / base;
    acum.push(f);
  }
  const primeiro = eixo.findIndex((_, i) => (i > 0 ? V[i - 1] : 0) + A[i] > 0.005);
  const dataBase = primeiro > 0 ? eixo[primeiro - 1] : addDays(eixo[0], -1);
  const fim = eixo[n - 1];
  const fCdi = diasUteis.filter(d => d > dataBase && d <= fim).reduce((a, d) => a * (1 + cdi.get(d) / 100), 1);
  const ultimoIpca = [...ipca.keys()].sort().at(-1);
  let fIpca = 1;
  let ateIpca = null;
  for (const d of diasUteis) {
    if (d <= dataBase || d > fim) continue;
    const m = d.slice(0, 7);
    if (m > ultimoIpca) break;
    fIpca *= Math.pow(1 + ipca.get(m) / 100, 1 / duMes.get(m));
    ateIpca = d;
  }
  const iAte = eixo.findLastIndex(x => x <= ateIpca);
  const twr = f - 1;
  const real = (acum[iAte]) / fIpca - 1;
  return { tipo, twr: twr * 100, cdi: (fCdi - 1) * 100, pctCdi: twr / (fCdi - 1) * 100, inflation: (fIpca - 1) * 100,
    real: real * 100, valorFinal: r2(V[n - 1]), velho };
}

function evento(cotas, e) {
  if (cotas <= 0) return cotas;
  if (e.t === 'DESDOBRAMENTO') return Math.floor(cotas * (1 + e.f / 100) + 1e-9);
  if (e.t === 'GRUPAMENTO') return Math.floor(cotas * e.f + 1e-9);
  return cotas + Math.floor(cotas * e.f / 100 + 1e-9); // bonificacao
}

function ultimoAte(datas, d) {
  let lo = 0, hi = datas.length - 1, ans = -1;
  while (lo <= hi) { const m = (lo + hi) >> 1; if (datas[m] <= d) { ans = m; lo = m + 1; } else hi = m - 1; }
  return ans;
}

const tk = await token();
let falhas = 0;
for (const tipo of TIPOS) {
  const meu = conferir(tipo);
  if (!meu) { console.log(`${tipo}: sem posicao`); continue; }
  const r = await fetch(`${API}/api/v1/performance/summary?type=${tipo}&basis=GROSS&range=MAX&benchmarks=CDI`,
    { headers: { Authorization: `Bearer ${tk}` } });
  const api = await r.json();
  const linhas = [
    ['TWR', meu.twr, api.twr], ['CDI', meu.cdi, api.cdi], ['% do CDI', meu.pctCdi, api.percentOfCdi],
    ['IPCA', meu.inflation, api.inflation], ['Real', meu.real, api.real], ['Valor final', meu.valorFinal, api.valueEnd],
  ];
  console.log(`\n== ${tipo}${meu.velho ? ` (${meu.velho} dias com preco de mais de 5 pregoes)` : ''}`);
  for (const [nome, a, b] of linhas) {
    const diff = b == null ? NaN : Math.abs(a - b);
    const ok = nome === 'Valor final' ? diff <= 0.011 : diff <= TOL;
    if (!ok) falhas++;
    console.log(`  ${nome.padEnd(12)} conferencia ${a.toFixed(4).padStart(12)}   API ${String(b).padStart(12)}   ${ok ? 'OK' : 'DIVERGE'}`);
  }
}
console.log(falhas ? `\n${falhas} divergencia(s)` : '\nTudo confere.');
process.exitCode = falhas ? 1 : 0;
