// Dispara uma rotina de administrador do PrimeVest pela API (POST /api/v1/admin/<rotina>).
//
// Uso (na raiz do projeto, ou pelo conteiner: docker compose --profile carga run --rm tools node carga/scripts/admin.mjs ...):
//   node carga/scripts/admin.mjs bootstrap          carga inicial num banco vazio (README.md)
//   node carga/scripts/admin.mjs issuers/import     emissores: companhias, FIIs e classificacao B3
//   node carga/scripts/admin.mjs logos | closing | proventos | catalog-audit | cvm/proventos
//   node carga/scripts/admin.mjs indices            indices de mercado 2010+ (docs/planos/PLANO_INDICES_MERCADO.md)
//   node carga/scripts/admin.mjs "indices?codes=CDI,IPCA&from=2024"   so alguns indices / anos
//   node carga/scripts/admin.mjs "indices?recent=true"                rodada incremental (a da rotina das 10:00)
//   node carga/scripts/admin.mjs tesouro            preco oficial do Tesouro 2010+ (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md)
//
// Duas APIs (docs/planos/PLANO_ADM_API.md): o login e na app-api (API_PORT, 8080); as rotinas de
// catalogo na adm-api (ADM_API_PORT, 8081). Na app-api so o extrato.
// Opcoes: --api URL  --adm URL  (padrao: PRIMEVEST_API_URL / PRIMEVEST_ADM_URL, senao localhost nas portas do env)
//         --email guilhermeunatec@gmail.com
//         --json arquivo.json   corpo da requisicao
//         --body '{"a":1}'      corpo inline;  --method PUT  (padrao POST)
//   ex.: node carga/scripts/admin.mjs assets/123/unit-composition --method PUT --body '{"ON":1,"PNA":2}'
// Senha: PRIMEVEST_SENHA do ambiente ou do .env do projeto; sem ela, pedida no terminal.
// As rotinas rodam em segundo plano na API (resposta 202); o resultado sai no
// log: docker logs -f primevest-adm-api (extrato: primevest-app-api)
import { readFileSync } from 'node:fs';
import { apiUrls, loadEnv, option } from './lib/env.mjs';

const args = process.argv.slice(2);
const ROTINA = args.find((a, i) => !a.startsWith('--') && !(i > 0 && args[i - 1].startsWith('--')));
const ENV = loadEnv();
const { api: API, adm: ADM } = apiUrls(ENV, args);
// rotinas que ficaram na app-api (carteira); o resto e da adm-api (docs/planos/PLANO_ADM_API.md)
const NA_APP = ['proventos/extrato'];
const EMAIL = option(args, '--email', ENV.PRIMEVEST_EMAIL || 'guilhermeunatec@gmail.com');
const JSON_FILE = option(args, '--json', null);
const BODY = option(args, '--body', null);
const METHOD = option(args, '--method', 'POST').toUpperCase();

if (!ROTINA) {
  console.error('Informe a rotina. Ex.: node carga/scripts/admin.mjs bootstrap');
  process.exit(2);
}

function perguntaSenha(texto) {
  return new Promise((ok, falha) => {
    const entrada = process.stdin;
    if (!entrada.isTTY) {
      falha(new Error('Sem terminal interativo: defina PRIMEVEST_SENHA (ambiente ou .env) para rodar assim.'));
      return;
    }
    process.stdout.write(texto);
    let valor = '';
    entrada.setRawMode(true);
    entrada.resume();
    entrada.setEncoding('utf8');
    const aoDigitar = (pedaco) => {
      for (const c of pedaco) {
        if (c === '\r' || c === '\n') {
          entrada.setRawMode(false);
          entrada.pause();
          entrada.off('data', aoDigitar);
          process.stdout.write('\n');
          ok(valor);
          return;
        }
        if (c === '\u0003') {
          entrada.setRawMode(false);
          process.stdout.write('\n');
          process.exit(130);
        }
        if (c === '\u0008' || c === '\u007f') {
          if (valor.length) { valor = valor.slice(0, -1); process.stdout.write('\b \b'); }
          continue;
        }
        if (c >= ' ') { valor += c; process.stdout.write('*'); }
      }
    };
    entrada.on('data', aoDigitar);
  });
}

async function main() {
  const senha = ENV.PRIMEVEST_SENHA || await perguntaSenha(`Senha de ${EMAIL}: `);
  // No Windows, sair com o stdin ainda aberto em modo bruto derruba o libuv
  // ("Assertion failed: !(handle->flags & UV_HANDLE_CLOSING)"): solta o stdin
  // e deixa o processo terminar sozinho, sem process.exit().
  if (process.stdin.isTTY) process.stdin.destroy();
  const login = await fetch(`${API}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: EMAIL, password: senha }),
  });
  if (!login.ok) {
    console.error(`Login recusado em ${API} (${login.status}): ${await login.text()}`);
    return 1;
  }
  const { accessToken } = await login.json();
  const corpo = JSON_FILE ? readFileSync(JSON_FILE, 'utf8') : BODY;
  const destino = NA_APP.includes(ROTINA) ? API : ADM;
  const r = await fetch(`${destino}/api/v1/admin/${ROTINA}`, {
    method: METHOD,
    headers: corpo
      ? { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' }
      : { Authorization: `Bearer ${accessToken}` },
    body: corpo ?? undefined,
  });
  console.log(`${r.status} ${await r.text()}`);
  return r.ok ? 0 : 1;
}

process.exitCode = await main();
