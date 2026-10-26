// Conferencia das metas contra as telas de origem (docs/planos/PLANO_METAS.md, criterio M-A2), pela API e sem gravar
// (POST /goals/preview): patrimonio = Patrimonio bruto do Dashboard; renda de 12 meses corridos = Proventos 12 meses
// do Dashboard; ALO-2 = Rebalanceamento; RNT-1 dos ultimos 12 meses = TWR bruto da Rentabilidade (12M).
//
// Uso: node carga/scripts/conferencia/metas.mjs   (senha: ACTIVEST_PASSWORD do .env). Sai com 1 se algo nao bater.
import { apiUrls, loadEnv, option } from '../lib/env.mjs';

const args = process.argv.slice(2);
const ENV = loadEnv();
const { api: API } = apiUrls(ENV, args);
const EMAIL = option(args, '--email', ENV.ACTIVEST_EMAIL || 'guilhermeunatec@gmail.com');

async function main() {
  const login = await fetch(`${API}/api/v1/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: EMAIL, password: ENV.ACTIVEST_PASSWORD }),
  });
  if (!login.ok) throw new Error(`login ${login.status}`);
  const { accessToken } = await login.json();
  const h = { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' };
  const get = async (p) => (await fetch(`${API}/api/v1${p}`, { headers: h })).json();
  const preview = async (body) => {
    const r = await fetch(`${API}/api/v1/goals/preview`, { method: 'POST', headers: h, body: JSON.stringify(body) });
    if (!r.ok) throw new Error(`preview ${body.type}: ${r.status} ${await r.text()}`);
    return (await r.json()).evaluation;
  };
  const fig = (e, k) => e.figures.find((f) => f.key === k)?.value;
  const rows = [];
  const check = (nome, meta, tela, tol = 0.005) => {
    const ok = meta != null && tela != null && Math.abs(Number(meta) - Number(tela)) <= tol;
    rows.push({ nome, meta: Number(meta), tela: Number(tela), ok });
  };

  const summary = await get('/portfolio/');
  const pat = await preview({ name: 'c', type: 'PAT_1', targetValue: 1e9, scope: { reservation: 'WITH' } });
  check('Patrimônio (PAT-1, com reserva) × Dashboard', pat.indicator, summary.currentValue);

  const dividends = await get('/dividends/');
  const ren = await preview({ name: 'c', type: 'REN_1', targetValue: 1e6 });
  check('Proventos 12 meses corridos (REN-1) × Dashboard', fig(ren, 'rolling12m'), dividends.last12Months);

  const reb = await get('/strategy/rebalance');
  const alo = await preview({ name: 'c', type: 'ALO_2', targetValue: 5 });
  for (const i of reb.items) {
    const row = alo.items.find((x) => x.item === i.typeName);
    check(`% atual ${i.typeName} (ALO-2) × Rebalanceamento`, row ? row.current : null, i.currentPercentage);
  }

  const perf = await get('/performance/summary?range=1A&basis=GROSS');
  const rnt = await preview({ name: 'c', type: 'RNT_1', targetValue: 0, params: { period: 'LAST_12M' } });
  if (rnt.indicator == null && perf.twr == null) {
    rows.push({ nome: `Rentabilidade 12M indisponível nas duas (${rnt.situationNote}; tela: ${perf.coverage?.unavailable?.join(', ')})`,
      meta: null, tela: null, ok: true });
  } else {
    check('Rentabilidade 12M (RNT-1) × Rentabilidade (TWR bruto 1A)', rnt.indicator, perf.twr, 0.01);
  }
  // por tipo (o recorte da tela Rentabilidade): as acoes e os FIIs
  for (const tipo of ['ACAO', 'FII']) {
    const p = await get(`/performance/summary?range=1A&basis=GROSS&type=${tipo}`);
    const g = await preview({ name: 'c', type: 'RNT_1', targetValue: 0, params: { period: 'LAST_12M' },
      scope: { kind: 'TYPE', values: [tipo] } });
    check(`Rentabilidade 12M ${tipo} (RNT-1) × Rentabilidade`, g.indicator ?? null, p.twr ?? null, 0.01);
  }

  for (const r of rows) console.log(`${r.ok ? 'ok ' : 'ERRO'} ${r.nome}${r.meta === null ? '' : `: meta ${r.meta} · tela ${r.tela}`}`);
  return rows.every((r) => r.ok) ? 0 : 1;
}

try {
  process.exitCode = await main();
} catch (e) {
  console.error(`ERRO: ${e.message}`);
  process.exitCode = 1;
}
