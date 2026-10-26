// Traz a carteira do usuario do PrimeVest (banco antigo) para o Activest (docs/planos/PLANO_METAS.md, 5.3 — C1).
//
// Le o banco antigo SO PARA LEITURA e relanca pela API do Activest, em ordem cronologica, as transacoes MANUAIS do
// usuario (as importadas da Binance, origem AUTO, ficam de fora — I2). Cada transacao passa pelas regras de hoje
// (titulo de RF, FIFO do Tesouro por distribuidora, lotes e IR/IOF da venda de RF, proventos, valorizacao).
// Depois: reserva de emergencia das posicoes, estrategias, estrategia da reserva, custo de bonificacao informado e os
// proventos manuais (sem endpoint: SQL direto). No fim roda o POSITIONS_REFRESH e confere posicao por posicao.
//
// Uso (na raiz do projeto, com o compose do Activest no ar):
//   node carga/scripts/primevest/importar_carteira.mjs --dry-run    lista o que seria lancado, sem gravar
//   node carga/scripts/primevest/importar_carteira.mjs              carrega e confere
//   node carga/scripts/primevest/importar_carteira.mjs --conferir   so a conferencia (carteira ja carregada)
// Opcoes: --antigo primevest-db/db_primevest  --novo activest-db/db_activest  --email guilhermeunatec@gmail.com
//         --api URL  --adm URL (padrao: localhost nas portas do .env)
// Senha: ACTIVEST_PASSWORD do .env (a mesma conta nos dois bancos, casada pelo e-mail).
//
// Regras: recusa carteira do Activest que ja tenha transacao (recarga = banco novo); nunca grava no banco antigo; se o
// conteiner antigo estava parado, sobe e para no fim. Sai com 1 se a conferencia achar diferenca sem explicacao.
import { execFileSync } from 'node:child_process';
import { apiUrls, loadEnv, option } from '../lib/env.mjs';

const args = process.argv.slice(2);
const ENV = loadEnv();
const { api: API, adm: ADM } = apiUrls(ENV, args);
const EMAIL = option(args, '--email', ENV.ACTIVEST_EMAIL || 'guilhermeunatec@gmail.com').toLowerCase();
const ANTIGO = option(args, '--antigo', 'primevest-db/db_primevest');
const NOVO = option(args, '--novo', 'activest-db/db_activest');
const DRY = args.includes('--dry-run');
const SO_CONFERIR = args.includes('--conferir');

// ---------------------------------------------------------------- banco (psql do conteiner)
const docker = (a, input) => execFileSync('docker', a, { input, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 }).replace(/\r/g, '');
const lit = (v) => (v === null || v === undefined ? 'NULL' : `'${String(v).replace(/'/g, "''")}'`);
function sql(spec, q) {
  const [c, d] = spec.split('/');
  return docker(['exec', '-i', c, 'psql', '-U', 'postgres', '-d', d, '-At', '-v', 'ON_ERROR_STOP=1'], q).trim();
}
/** Linhas de uma consulta como objetos (json_agg). */
function rows(spec, q) {
  const out = sql(spec, `select coalesce(json_agg(x), '[]') from (${q}) x;`);
  return JSON.parse(out || '[]');
}

function containerRunning(name) {
  try { return docker(['inspect', '-f', '{{.State.Running}}', name]).trim() === 'true'; } catch { return null; }
}

// ---------------------------------------------------------------- API
let TOKEN = null;
async function login() {
  const senha = ENV.ACTIVEST_PASSWORD;
  if (!senha) throw new Error('Defina ACTIVEST_PASSWORD no .env da raiz.');
  const r = await fetch(`${API}/api/v1/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: EMAIL, password: senha }),
  });
  if (!r.ok) throw new Error(`Login recusado em ${API} (${r.status}): ${await r.text()}`);
  TOKEN = (await r.json()).accessToken;
}
async function call(method, base, path, body) {
  const r = await fetch(`${base}${path}`, {
    method,
    headers: body !== undefined
      ? { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' }
      : { Authorization: `Bearer ${TOKEN}` },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await r.text();
  if (!r.ok) {
    let msg = text;
    try { msg = JSON.parse(text).message || text; } catch { /* texto puro */ }
    const e = new Error(`${method} ${path} -> ${r.status}: ${msg}`);
    e.status = r.status;
    throw e;
  }
  return text ? JSON.parse(text) : null;
}
const sleep = (ms) => new Promise((ok) => setTimeout(ok, ms));

// ---------------------------------------------------------------- leitura do PrimeVest
function lerAntigo() {
  const user = rows(ANTIGO, `select u.id, p.id as portfolio_id from users u join portfolios p on p.user_id = u.id
                             where lower(u.email) = ${lit(EMAIL)}`)[0];
  if (!user) throw new Error(`Usuario ${EMAIL} nao encontrado no PrimeVest.`);
  const pid = lit(user.portfolio_id);
  const transacoes = rows(ANTIGO, `
    select t.id, t.transaction_date::text as data, t.created_at::text as criada, t.type as operacao, t.origin,
           t.shares, t.price_per_share as preco, t.total_amount as total, t.brokerage_fee as taxa,
           t.net_amount as liquido, t.sale_group_id as grupo,
           a.ticker, ty.name as tipo, pa.id as posicao, pa.is_reservation as reserva, pa.is_settled as liquidada,
           n.id as titulo, n.maturity_date::text as vencimento, n.rate_type, n.indexer, n.indexer_rate, n.paper_code,
           i.cnpj_root as emissora, d.cnpj_root as distribuidora
      from transactions t
      join portfolio_assets pa on pa.id = t.portfolio_asset_id
      join assets a on a.id = pa.asset_id
      join asset_types ty on ty.id = a.asset_type_id
      left join fixed_income_notes n on n.id = t.note_id
      left join fixed_income_issuers i on i.id = n.issuer_id
      left join fixed_income_issuers d on d.id = n.distributor_id
     where pa.portfolio_id = ${pid}
     order by t.transaction_date, t.created_at, t.id`);
  const estrategias = rows(ANTIGO, `select ty.name as tipo, s.target_percentage as percentual
      from strategies s join asset_types ty on ty.id = s.asset_type_id where s.user_id = ${lit(user.id)}`);
  const reserva = rows(ANTIGO, `select type_reservation as tipo, target_percentage as percentual, target_value as valor
      from strategy_reservations where user_id = ${lit(user.id)}`)[0] || null;
  const custos = rows(ANTIGO, `select c.portfolio_asset_id as posicao, a.ticker, e.type, e.data_com::text as data_com,
           c.cost_per_share as custo
      from stock_event_costs c join asset_stock_events e on e.id = c.stock_event_id join assets a on a.id = e.asset_id
      join portfolio_assets pa on pa.id = c.portfolio_asset_id where pa.portfolio_id = ${pid}`);
  const proventosManuais = rows(ANTIGO, `select dv.portfolio_asset_id as posicao, a.ticker, dv.amount_per_share,
           dv.total_amount, dv.ex_date::text as ex_date, dv.payment_date::text as payment_date, dv.type, dv.shares_on_date,
           dv.withholding_rate, dv.withholding_tax, dv.net_amount_per_share, dv.net_amount,
           w.dividend_type as regra_tipo, wt.name as regra_tipo_ativo, w.valid_from::text as regra_desde
      from dividends dv join portfolio_assets pa on pa.id = dv.portfolio_asset_id join assets a on a.id = pa.asset_id
      left join withholding_tax_rules w on w.id = dv.withholding_rule_id left join asset_types wt on wt.id = w.asset_type_id
     where pa.portfolio_id = ${pid} and dv.origin = 'MANUAL' order by dv.ex_date, dv.id`);
  return { user, transacoes, estrategias, reserva, custos, proventosManuais };
}

// ---------------------------------------------------------------- o que vai ser lancado
/** Agrupa as partes de uma venda do Tesouro (mesmo sale_group_id) numa venda so, e tira as importadas. */
function planejar(transacoes) {
  const fora = transacoes.filter((t) => t.origin !== 'MANUAL');
  const manuais = transacoes.filter((t) => t.origin === 'MANUAL');
  const ultimaDoTitulo = new Map();
  for (const t of manuais) if (t.titulo) ultimaDoTitulo.set(t.titulo, t.id);
  const lancamentos = [];
  const grupos = new Map();
  for (const t of manuais) {
    if (t.grupo) {
      const g = grupos.get(t.grupo);
      if (g) { g.partes.push(t); continue; }
      const novo = { ...t, partes: [t] };
      grupos.set(t.grupo, novo);
      lancamentos.push(novo);
      continue;
    }
    lancamentos.push({ ...t, partes: [t], ultimaDoTitulo: t.titulo && ultimaDoTitulo.get(t.titulo) === t.id });
  }
  for (const g of grupos.values()) {
    g.shares = g.partes.reduce((s, p) => s + Number(p.shares), 0).toFixed(8);
    g.total = g.partes.reduce((s, p) => s + Number(p.total), 0).toFixed(2);
    g.taxa = g.partes.reduce((s, p) => s + Number(p.taxa || 0), 0).toFixed(2);
  }
  return { lancamentos, fora };
}

function descrever(l) {
  const qtd = l.shares ? `${Number(l.shares)} x ${Number(l.preco)}` : `R$ ${l.operacao === 'SELL' && l.liquido ? l.liquido + ' (líquido)' : l.total}`;
  const extra = l.partes.length > 1 ? ` [${l.partes.length} partes no PrimeVest, uma venda aqui]` : '';
  return `${l.data} ${l.operacao === 'BUY' ? 'compra' : 'venda '} ${l.ticker.padEnd(40)} ${qtd}${extra}`;
}

// ---------------------------------------------------------------- carga
async function carregar(antigo, plano, novoPortfolioId) {
  const emissoraNova = new Map(rows(NOVO, 'select cnpj_root, id from fixed_income_issuers')
    .map((r) => [r.cnpj_root, r.id]));
  const idEmpresa = (raiz) => {
    if (!raiz) return null;
    const id = emissoraNova.get(raiz);
    if (!id) throw new Error(`Empresa de renda fixa ${raiz} nao existe no Activest.`);
    return id;
  };
  const tituloNovo = new Map();   // titulo antigo -> noteId no Activest
  const posicaoNova = new Map();  // posicao antiga -> portfolioAssetId no Activest
  let n = 0;
  for (const l of plano.lancamentos) {
    const rf = l.tipo === 'RENDA_FIXA';
    const td = l.tipo === 'TESOURO_DIRETO';
    const dto = {
      ticker: l.ticker, type: l.operacao, transactionDate: l.data,
      brokerageFee: l.taxa != null ? Number(l.taxa) : 0,
    };
    if (rf) {
      dto.totalAmount = Number(l.operacao === 'SELL' && l.liquido != null ? l.liquido : l.total);
      const ja = tituloNovo.get(l.titulo);
      if (ja) {
        dto.noteId = ja;
      } else {
        Object.assign(dto, {
          issuerId: idEmpresa(l.emissora), distributorId: idEmpresa(l.distribuidora),
          maturityDate: l.vencimento, rateType: l.rate_type, indexer: l.indexer,
          indexerRate: l.indexer_rate != null ? Number(l.indexer_rate) : null, paperCode: l.paper_code,
        });
      }
      if (l.operacao === 'SELL') dto.isFullSettlement = Boolean(l.ultimaDoTitulo && l.liquidada);
    } else {
      dto.shares = Number(l.shares);
      dto.pricePerShare = Number(l.preco);
    }
    if (td) {
      dto.distributorId = idEmpresa(l.distribuidora);
      if (l.operacao === 'BUY') Object.assign(dto, {
        rateType: l.rate_type, indexer: l.indexer,
        indexerRate: l.indexer_rate != null ? Number(l.indexer_rate) : null,
      });
    }
    if (l.operacao === 'BUY' && l.reserva) dto.isReservation = true;
    let resp;
    try {
      resp = await call('POST', API, '/api/v1/transactions/', dto);
    } catch (e) {
      throw new Error(`Falhou em ${descrever(l)}\n  ${e.message}`);
    }
    n++;
    if (rf && resp.noteId && !tituloNovo.has(l.titulo)) tituloNovo.set(l.titulo, resp.noteId);
    if (l.operacao === 'BUY' && resp.portfolioAssetId) posicaoNova.set(l.posicao, resp.portfolioAssetId);
    console.log(`  ok ${descrever(l)}`);
  }

  // Reserva de emergencia: o estado final de cada posicao antiga (o isReservation da compra cobre a criacao).
  const reservas = new Map();
  for (const t of antigo.transacoes) if (t.origin === 'MANUAL') reservas.set(t.posicao, t.reserva);
  let nReserva = 0;
  for (const [antiga, reserva] of reservas) {
    const nova = posicaoNova.get(antiga);
    if (!nova || !reserva) continue;
    await call('PATCH', API, `/api/v1/portfolio/assets/${nova}/reservation`, { isReservation: true });
    nReserva++;
  }

  // Estrategias (tipo por nome) e estrategia da reserva.
  const tiposNovos = new Map(rows(NOVO, 'select name, id from asset_types').map((r) => [r.name, r.id]));
  if (antigo.estrategias.length) {
    await call('PUT', API, '/api/v1/strategy/', {
      items: antigo.estrategias.map((s) => ({ assetTypeId: tiposNovos.get(s.tipo), targetPercentage: Number(s.percentual) })),
    });
  }
  if (antigo.reserva) {
    await call('PUT', API, '/api/v1/strategy/reservation', {
      typeReservation: antigo.reserva.tipo === 2 ? 'VALUE' : 'PERCENTAGE',
      targetPercentage: antigo.reserva.percentual != null ? Number(antigo.reserva.percentual) : null,
      targetValue: antigo.reserva.valor != null ? Number(antigo.reserva.valor) : null,
    });
  }

  // Custo de bonificacao informado: evento casado por ticker, tipo e data-com.
  let nCusto = 0;
  for (const c of antigo.custos) {
    const nova = posicaoNova.get(c.posicao);
    const ev = rows(NOVO, `select e.id from asset_stock_events e join assets a on a.id = e.asset_id
        where a.ticker = ${lit(c.ticker)} and e.type = ${lit(c.type)} and e.data_com = ${lit(c.data_com)}`)[0];
    if (!nova || !ev) { console.log(`  ! custo de bonificacao de ${c.ticker} (${c.data_com}) sem posicao/evento no Activest`); continue; }
    await call('PUT', API, `/api/v1/portfolio/assets/${nova}/stock-events/${ev.id}/cost`, { costPerShare: Number(c.custo) });
    nCusto++;
  }

  // Proventos manuais: nao ha endpoint; SQL direto, com a regra de IR casada pela chave natural.
  let nManual = 0;
  for (const d of antigo.proventosManuais) {
    const nova = posicaoNova.get(d.posicao);
    if (!nova) { console.log(`  ! provento manual de ${d.ticker} sem posicao no Activest`); continue; }
    const regra = d.regra_tipo
      ? `(select w.id from withholding_tax_rules w join asset_types t on t.id = w.asset_type_id
           where w.dividend_type = ${lit(d.regra_tipo)} and t.name = ${lit(d.regra_tipo_ativo)}
             and w.valid_from is not distinct from ${d.regra_desde ? lit(d.regra_desde) + '::date' : 'NULL'})`
      : 'NULL';
    sql(NOVO, `insert into dividends (portfolio_asset_id, amount_per_share, total_amount, ex_date, payment_date, type,
                 shares_on_date, origin, withholding_rule_id, withholding_rate, withholding_tax, net_amount_per_share,
                 net_amount, tax_checked_at)
               values (${lit(nova)}, ${d.amount_per_share}, ${d.total_amount}, ${lit(d.ex_date)}, ${lit(d.payment_date)},
                 ${lit(d.type)}, ${d.shares_on_date ?? 'NULL'}, 'MANUAL', ${regra}, ${d.withholding_rate ?? 'NULL'},
                 ${d.withholding_tax ?? 'NULL'}, ${d.net_amount_per_share ?? 'NULL'}, ${d.net_amount ?? 'NULL'}, now())`);
    nManual++;
  }
  console.log(`\nLançadas ${n} transações; reserva em ${nReserva} posições; ${antigo.estrategias.length} estratégias` +
    `${antigo.reserva ? ' + reserva' : ''}; ${nCusto} custo(s) de bonificação; ${nManual} provento(s) manual(is).`);
  void novoPortfolioId;
}

/** Roda o POSITIONS_REFRESH pela tela de Tarefas (adm-api) e espera terminar. */
async function refresh() {
  const tasks = await call('GET', ADM, '/api/v1/tasks');
  const t = tasks.find((x) => x.type === 'POSITIONS_REFRESH' || x.taskType === 'POSITIONS_REFRESH' || x.name === 'POSITIONS_REFRESH');
  if (!t) { console.log('  ! tarefa POSITIONS_REFRESH nao encontrada; rode pela tela de Tarefas'); return; }
  const run = await call('POST', ADM, `/api/v1/tasks/${t.id}/run`);
  process.stdout.write(`POSITIONS_REFRESH (rodada ${run.executionId}) `);
  for (let i = 0; i < 180; i++) {
    await sleep(2000);
    const e = await call('GET', ADM, `/api/v1/tasks/executions/${run.executionId}`);
    const status = e.status || e.execution?.status;
    if (status && status !== 'RUNNING') { console.log(status); return; }
    process.stdout.write('.');
  }
  console.log('ainda rodando — a conferência pode sair antes do fim');
}

// ---------------------------------------------------------------- conferencia (C3)
const POSICOES = (pid) => `
  select a.ticker, coalesce(n.purchase_date::text, '') as compra, coalesce(n.paper_code, '') as codigo,
         coalesce(dist.cnpj_root, '') as distribuidora, coalesce(n.indexer_rate::text, '') as taxa,
         pa.is_reservation as reserva, pa.is_settled as liquidada,
         count(t.id) as transacoes,
         round(coalesce(sum(case when t.type = 'BUY' then t.shares end), 0), 8)::text as cotas_compradas,
         round(coalesce(sum(case when t.type = 'SELL' then t.shares end), 0), 8)::text as cotas_vendidas,
         coalesce(sum(case when t.type = 'BUY' then t.total_amount end), 0)::text as compras,
         coalesce(sum(case when t.type = 'SELL' then coalesce(t.net_amount, t.total_amount) end), 0)::text as vendas_liquido,
         coalesce(sum(case when t.type = 'SELL' then t.total_amount end), 0)::text as vendas_bruto,
         (select count(*) from dividends d where d.portfolio_asset_id = pa.id) as proventos,
         (select coalesce(sum(coalesce(d.net_amount, d.total_amount)), 0)::text from dividends d where d.portfolio_asset_id = pa.id) as proventos_liquido
    from portfolio_assets pa
    join assets a on a.id = pa.asset_id
    left join fixed_income_notes n on n.id = pa.note_id
    left join fixed_income_issuers dist on dist.id = n.distributor_id
    left join transactions t on t.portfolio_asset_id = pa.id
   where pa.portfolio_id = ${lit(pid)}
     and (select count(*) from transactions x where x.portfolio_asset_id = pa.id and x.origin <> 'MANUAL') = 0
   group by pa.id, a.ticker, n.purchase_date, n.paper_code, dist.cnpj_root, n.indexer_rate
   order by 1, 2, 5`;

function conferir(pidAntigo, pidNovo) {
  const chave = (p) => [p.ticker, p.compra, p.codigo, p.distribuidora, p.taxa].join(' | ');
  const antigos = new Map(rows(ANTIGO, POSICOES(pidAntigo)).map((p) => [chave(p), p]));
  const novos = new Map(rows(NOVO, POSICOES(pidNovo)).map((p) => [chave(p), p]));
  const campos = ['reserva', 'liquidada', 'transacoes', 'cotas_compradas', 'cotas_vendidas', 'compras', 'vendas_liquido',
    'vendas_bruto', 'proventos', 'proventos_liquido'];
  const diferencas = [];
  for (const [k, a] of antigos) {
    const b = novos.get(k);
    if (!b) { diferencas.push(`${k}: posição não existe no Activest`); continue; }
    for (const c of campos) {
      if (String(a[c]) !== String(b[c])) diferencas.push(`${k}: ${c} PrimeVest=${a[c]} Activest=${b[c]}`);
    }
  }
  for (const k of novos.keys()) if (!antigos.has(k)) diferencas.push(`${k}: posição só existe no Activest`);
  console.log(`\nConferência: ${antigos.size} posições no PrimeVest (sem as da Binance), ${novos.size} no Activest.`);
  if (!diferencas.length) { console.log('Sem diferenças.'); return 0; }
  console.log(`${diferencas.length} diferença(s):`);
  for (const d of diferencas) console.log(`  - ${d}`);
  return 1;
}

// ---------------------------------------------------------------- principal
async function main() {
  const antigoConteiner = ANTIGO.split('/')[0];
  const estado = containerRunning(antigoConteiner);
  if (estado === null) throw new Error(`Conteiner ${antigoConteiner} nao existe.`);
  if (!estado) {
    console.log(`Subindo ${antigoConteiner} (so leitura; para no fim)...`);
    docker(['start', antigoConteiner]);
    for (let i = 0; i < 30; i++) {
      try { sql(ANTIGO, 'select 1'); break; } catch { await sleep(1000); }
    }
  }
  try {
    const antigo = lerAntigo();
    const plano = planejar(antigo.transacoes);
    console.log(`PrimeVest: ${antigo.transacoes.length} transações; ${plano.fora.length} importadas da Binance ficam de fora; ` +
      `${plano.lancamentos.length} lançamentos (vendas repartidas do Tesouro juntas).`);
    const novo = rows(NOVO, `select p.id as portfolio_id,
        (select count(*) from transactions t join portfolio_assets pa on pa.id = t.portfolio_asset_id
          where pa.portfolio_id = p.id) as transacoes
        from users u join portfolios p on p.user_id = u.id where lower(u.email) = ${lit(EMAIL)}`)[0];
    if (!novo) throw new Error(`Usuario ${EMAIL} sem carteira no Activest.`);

    if (DRY) {
      for (const l of plano.lancamentos) console.log(`  ${descrever(l)}`);
      console.log(`\nJunto: ${antigo.estrategias.length} estratégias${antigo.reserva ? ' + reserva' : ''}, ` +
        `${antigo.custos.length} custo(s) de bonificação, ${antigo.proventosManuais.length} provento(s) manual(is). Nada gravado.`);
      return 0;
    }
    if (!SO_CONFERIR) {
      if (Number(novo.transacoes) > 0) {
        throw new Error(`A carteira do Activest já tem ${novo.transacoes} transação(ões). A carga só roda numa carteira vazia.`);
      }
      await login();
      await carregar(antigo, plano, novo.portfolio_id);
      await refresh();
    }
    return conferir(antigo.user.portfolio_id, novo.portfolio_id);
  } finally {
    if (!estado) { docker(['stop', antigoConteiner]); console.log(`${antigoConteiner} parado de novo.`); }
  }
}

try {
  process.exitCode = await main();
} catch (e) {
  console.error(`\nERRO: ${e.message}`);
  process.exitCode = 1;
}
