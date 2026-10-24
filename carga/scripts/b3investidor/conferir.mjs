// Ponta a ponta da integração com a B3 contra o simulador (docs/planos/PLANO_INTEGRACAO_B3.md, F5 e 6.11).
// Conecta o CPF fictício do simulador, autoriza na página de opt-in do simulador, sincroniza, confirma e confere:
//   C1  carteira de renda variável da pilha testada x carteira gabarito (cotas e custo por ativo; a diferença de
//       proventos sai como informação: depende do catálogo de cada pilha, não da importação)
//   C2  grupos negociados (B1) x "Transferência - Liquidação" (contagem e datas)
//   C3  proventos informados pela B3 x proventos da carteira, depois de confirmar (resultado no motivo de cada item)
//   C4  segunda sincronização: nada novo
//   C5  (--revogar) revogar no simulador -> a próxima sincronização encerra a conexão
//   C7  Tesouro Direto da pilha testada x gabarito (F7: compras e vendas de Tesouro pela Movimentação)
//
// Uso (na raiz; pilha activest-teste no ar com --profile b3mock e B3I_* do .env.teste):
//   node carga/scripts/b3investidor/conferir.mjs --api http://localhost:8082 --mock http://localhost:8190 \
//        [--gabarito http://localhost:8080] [--cpf 52998224725] [--sem-confirmar] [--revogar]
// Senha: ACTIVEST_PASSWORD do ambiente ou do .env (a mesma do administrador semeado nas duas pilhas).
// Não imprime CPF: só os 2 últimos dígitos.
import { loadEnv, option } from '../lib/env.mjs';

const args = process.argv.slice(2);
const ENV = loadEnv();
const API = option(args, '--api', 'http://localhost:8082').replace(/\/$/, '');
const MOCK = option(args, '--mock', 'http://localhost:8190').replace(/\/$/, '');
const GABARITO = option(args, '--gabarito', null);
const CPF = option(args, '--cpf', ENV.B3I_MOCK_DOCUMENT || '52998224725').replace(/\D/g, '');
const EMAIL = option(args, '--email', ENV.ACTIVEST_EMAIL || 'guilhermeunatec@gmail.com');
const SENHA = option(args, '--senha', ENV.ACTIVEST_PASSWORD);
const CONFIRMAR = !args.includes('--sem-confirmar');
const REVOGAR = args.includes('--revogar');
const RENDA_VARIAVEL = new Set(['Ações', 'FIIs', 'ETFs', 'BDRs']);
const TESOURO = new Set(['Tesouro Direto']);

const resultado = [];
const conferencia = (id, ok, detalhe) => {
  resultado.push({ id, ok, detalhe });
  console.log(`${ok ? 'OK   ' : 'FALHA'} ${id}  ${detalhe}`);
};

async function login(api) {
  const r = await fetch(`${api}/api/v1/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: EMAIL, password: SENHA }),
  });
  if (!r.ok) throw new Error(`Login recusado em ${api} (${r.status})`);
  const { accessToken } = await r.json();
  return async (metodo, caminho, corpo) => {
    const resp = await fetch(`${api}${caminho}`, {
      method: metodo,
      headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
    const texto = await resp.text();
    let json = null;
    try { json = texto ? JSON.parse(texto) : null; } catch { json = texto; }
    if (!resp.ok) throw new Error(`${metodo} ${caminho} -> ${resp.status}: ${typeof json === 'string' ? json : JSON.stringify(json)}`);
    return json;
  };
}

async function optin(acao) {
  const r = await fetch(`${MOCK}/optin/${acao}`, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `documentNumber=${CPF}`,
  });
  if (!r.ok) throw new Error(`Simulador /optin/${acao} -> ${r.status}`);
}

const BASE = '/api/v1/integrations/connections';

async function sincronizar(api, id) {
  const pedido = await api('POST', `${BASE}/${id}/preview`);
  let run = pedido;
  for (let i = 0; i < 150 && ['QUEUED', 'RUNNING'].includes(run.status); i++) {
    await new Promise((ok) => setTimeout(ok, 2000));
    run = await api('GET', `${BASE}/${id}/runs/${pedido.runId}`);
  }
  return run;
}

/** Linhas da carteira dos tipos pedidos, somadas por ticker (o Tesouro tem uma linha por título comprado). */
function carteira(linhas, tipos) {
  const m = new Map();
  for (const l of linhas) {
    if (!tipos.has(l.typeName) || Number(l.totalShares) === 0) continue;
    const a = m.get(l.ticker) ?? { totalShares: 0, totalInvested: 0, totalDividends: 0 };
    m.set(l.ticker, {
      totalShares: Math.round((a.totalShares + Number(l.totalShares)) * 1e8) / 1e8,
      totalInvested: a.totalInvested + Number(l.totalInvested ?? 0),
      totalDividends: a.totalDividends + Number(l.totalDividends ?? 0),
    });
  }
  return m;
}

function comparar(id, teste, ref, rotulo) {
  const tickers = [...new Set([...teste.keys(), ...ref.keys()])].sort();
  const dif = [];
  const info = [];
  for (const t of tickers) {
    const a = teste.get(t); const b = ref.get(t);
    const cotas = [a?.totalShares ?? 0, b?.totalShares ?? 0];
    const custo = [a?.totalInvested ?? 0, b?.totalInvested ?? 0];
    const prov = [a?.totalDividends ?? 0, b?.totalDividends ?? 0];
    if (cotas[0] !== cotas[1] || Math.abs(custo[0] - custo[1]) > 0.05) {
      dif.push(`${t}: cotas ${cotas[0]} x ${cotas[1]}, custo ${custo[0].toFixed(2)} x ${custo[1].toFixed(2)}`);
    } else if (Math.abs(prov[0] - prov[1]) > 0.05) {
      info.push(`${t}: proventos ${prov[0].toFixed(2)} x ${prov[1].toFixed(2)} (catálogo de cada pilha)`);
    }
  }
  conferencia(id, dif.length === 0, `${tickers.length} ${rotulo}; ${dif.length} com diferença de cotas ou custo`);
  dif.forEach((d) => console.log('   ' + d));
  info.forEach((d) => console.log('   info: ' + d));
}

const api = await login(API);

// --- conexão
let conexoes = await api('GET', BASE);
let b3 = conexoes.find((c) => c.provider === 'B3' && c.status !== 'REVOKED');
if (!b3) {
  b3 = await api('POST', BASE, { provider: 'B3', label: `B3 (simulador ****${CPF.slice(-2)})`,
    documentNumber: CPF, consentAccepted: true });
  console.log(`conexão criada: ${b3.status}${b3.authorizationUrl ? ` (autorizar em ${b3.authorizationUrl})` : ''}`);
}
if (b3.status === 'PENDING_AUTHORIZATION') {
  await optin('authorize');
  console.log('opt-in: autorizado na página do simulador');
}

// --- primeira sincronização
const p = await sincronizar(api, b3.id);
if (p.status !== 'AWAITING_CONFIRMATION') throw new Error(`Sincronização ${p.status}: ${p.error}`);
const itens = p.items;
const porTipo = itens.reduce((a, i) => ({ ...a, [i.kind]: (a[i.kind] || 0) + 1 }), {});
console.log(`pré-visualização: ${p.newCount} novas, ${p.existingCount} existentes, ${p.unmappedCount} não importadas,`
  + ` ${p.duplicateCount} possíveis duplicatas`);
console.log('por tipo:', JSON.stringify(porTipo));
const naoImportadas = {};
for (const i of itens.filter((x) => x.outcome === 'UNMAPPED' && x.kind === 'EQUITY_TRADE')) {
  naoImportadas[i.reason] = (naoImportadas[i.reason] || 0) + 1;
}
if (Object.keys(naoImportadas).length) console.log('negociações não importadas:', naoImportadas);

// C2 — grupos (B1) x liquidações
const grupos = itens.filter((i) => i.kind === 'EQUITY_TRADE');
const liquidacoes = itens.filter((i) => i.kind === 'SETTLEMENT');
const diasGrupos = new Set(grupos.map((i) => i.executedAt.slice(0, 10)));
const diasLiq = new Set(liquidacoes.map((i) => i.executedAt.slice(0, 10)));
conferencia('C2', grupos.length === liquidacoes.length && diasGrupos.size === diasLiq.size,
  `${grupos.length} grupos x ${liquidacoes.length} liquidações; ${diasGrupos.size} x ${diasLiq.size} datas`);

function conferirProventos(lista) {
  const proventos = lista.filter((i) => i.kind === 'CASH_EVENT');
  const c3 = {};
  for (const i of proventos) {
    const chave = /conferido/.test(i.reason) ? 'JA_EXISTE' : /data de pagamento/.test(i.reason) ? 'SEM_DATA'
      : /não está na carteira/.test(i.reason) ? 'SEM_EVENTO' : /não conferido/.test(i.reason) ? 'RECUSADO'
        : 'SEM_CONFERENCIA';
    (c3[chave] ||= []).push(i);
  }
  conferencia('C3', !c3.SEM_CONFERENCIA, Object.entries(c3).map(([k, v]) => `${k} ${v.length}`).join(', ')
    + ` (de ${proventos.length})`);
  for (const k of ['SEM_DATA', 'SEM_EVENTO', 'RECUSADO']) {
    for (const i of (c3[k] || [])) console.log(`   ${k.padEnd(10)} ${i.executedAt.slice(0, 10)} ${i.reason}`);
  }
}

if (!CONFIRMAR) {
  console.log('--sem-confirmar: a pré-visualização fica aguardando.');
  process.exit(0);
}
const conf = await api('POST', `${BASE}/${b3.id}/runs/${p.runId}/confirm`, {});
console.log(`confirmada: ${conf.status}, ${conf.items.filter((i) => i.resolution === 'IMPORTED').length} importadas`);
// C3 depois de confirmar: só então a carteira tem as posições e os proventos delas.
conferirProventos(conf.items);

// C1 e C7 — carteira x gabarito
if (GABARITO) {
  const gab = await login(GABARITO);
  const linhasTeste = await api('GET', '/api/v1/portfolio/assets');
  const linhasRef = await gab('GET', '/api/v1/portfolio/assets');
  comparar('C1', carteira(linhasTeste, RENDA_VARIAVEL), carteira(linhasRef, RENDA_VARIAVEL), 'ativos de renda variável');
  comparar('C7', carteira(linhasTeste, TESOURO), carteira(linhasRef, TESOURO), 'títulos do Tesouro');
}

// C4 — segunda sincronização
const s = await sincronizar(api, b3.id);
conferencia('C4', s.status === 'AWAITING_CONFIRMATION' && s.newCount === 0,
  `${s.status}, ${s.newCount} novas, ${s.unmappedCount} não importadas (foto do dia: proventos a receber)`);
if (s.status === 'AWAITING_CONFIRMATION') await api('POST', `${BASE}/${b3.id}/runs/${s.runId}/confirm`, {});

// C5 — revogação
if (REVOGAR) {
  await optin('revoke');
  const r = await sincronizar(api, b3.id);
  conexoes = await api('GET', BASE);
  const depois = conexoes.find((c) => c.id === b3.id);
  conferencia('C5', r.status === 'FAILED' && depois.status === 'REVOKED', `execução ${r.status}, conexão ${depois.status}: ${r.error}`);
}

const falhas = resultado.filter((r) => !r.ok).length;
console.log(falhas ? `${falhas} conferência(s) com falha` : 'todas as conferências OK');
process.exitCode = falhas ? 1 : 0;
