// Conferencia do Preco Teto contra a planilha PRECO TETO V2 (docs/planos/PLANO_PRECO_TETO.md, 8.2).
//
// Para cada ticker: pega os padroes do PrimeVest (GET /ceiling-prices/defaults/{ticker}), calcula com eles
// (POST /ceiling-prices/calculate) e grava entradas + resultado em JSON. Inclui a linha VALE3 da planilha, com as
// entradas dela. Depois, conferencia_preco_teto_xlsx.py monta a planilha no layout da V2, com as formulas dela,
// e a coluna do PrimeVest ao lado.
//
// Uso: node carga/scripts/conferencia_preco_teto.mjs [VALE3 PETR4 ...] [--out docs/preco-teto/x.json]
// Senha: PRIMEVEST_PASSWORD do ambiente ou do .env do projeto.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { ROOT, apiUrls, loadEnv, option } from './lib/env.mjs';

const args = process.argv.slice(2);
const ENV = loadEnv();
const { api: API } = apiUrls(ENV, args);
const EMAIL = option(args, '--email', ENV.PRIMEVEST_EMAIL || 'guilhermeunatec@gmail.com');
const hoje = new Date().toLocaleDateString('sv-SE', { timeZone: 'America/Sao_Paulo' });
const OUT = resolve(ROOT, option(args, '--out', `docs/preco-teto/conferencia_preco_teto_${hoje}.json`));
const pedidos = args.filter((a, i) => !a.startsWith('--') && !(i > 0 && args[i - 1].startsWith('--')));
const TICKERS = pedidos.length ? pedidos : ['VALE3', 'PETR4', 'ITUB4', 'BBAS3', 'TAEE11', 'WEGE3'];

if (!ENV.PRIMEVEST_PASSWORD) {
  console.error('Defina PRIMEVEST_PASSWORD (ambiente ou .env).');
  process.exit(2);
}
const login = await fetch(`${API}/api/v1/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: EMAIL, password: ENV.PRIMEVEST_PASSWORD }),
});
if (!login.ok) {
  console.error(`Login recusado em ${API} (${login.status})`);
  process.exit(1);
}
const { accessToken } = await login.json();
const headers = { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' };

const call = async (method, path, body) => {
  const r = await fetch(`${API}/api/v1${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  if (!r.ok) throw new Error(`${method} ${path} -> ${r.status} ${await r.text()}`);
  return r.json();
};

const linhas = [];
const calcula = async (rotulo, entradas) => {
  const resultado = await call('POST', '/ceiling-prices/calculate', entradas);
  linhas.push({ rotulo, entradas, resultado });
  console.log(`${rotulo}: teto ${resultado.ceilingPrice ?? '—'}, ideal ${resultado.idealPrice ?? '—'}`);
};

for (const t of TICKERS) {
  try {
    const d = await call('GET', `/ceiling-prices/defaults/${t}`);
    const entradas = {
      ticker: d.ticker,
      quarters: d.quarters.map(q => ({ periodEnd: q.periodEnd, originalValue: q.value, unit: 'UNIDADE', currency: 'BRL' })),
      shares: d.shares.value, roePct: d.roePct.value, payoutPct: d.payoutPct.value,
      discountRatePct: d.discountRatePct.value, perpetualGrowthPct: d.perpetualGrowthPct.value,
      safetyMarginPct: d.safetyMarginPct.value, price: d.price.value,
    };
    await calcula(`${d.ticker} (padroes do PrimeVest)`, entradas);
    if (t === 'VALE3') {
      // a linha da planilha PRECO_TETO_V2.xlsx, com as entradas dela (lucros projetados pelo Guilherme)
      const lucros = [9956933910, 6940807500, 9956933910, 6940807500];
      await calcula('VALE3 (linha da planilha V2)', {
        ...entradas,
        quarters: entradas.quarters.map((q, i) => ({ ...q, originalValue: lucros[i] })),
        shares: 4262140830, roePct: 19.26, payoutPct: 48.19, discountRatePct: 13.75, perpetualGrowthPct: 3,
        safetyMarginPct: 15, price: 67.63,
      });
    }
  } catch (e) {
    console.error(`${t}: ${e.message}`);
  }
}

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify({ geradoEm: new Date().toISOString(), api: API, linhas }, null, 2));
console.log(`Gravado: ${OUT}`);
