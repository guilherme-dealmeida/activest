// GET autenticado na app-api do Activest, para conferencia.
// Uso: node carga/scripts/get.mjs /api/v1/assets/PETR4/indicators [/outro/caminho ...] [--api URL]
// Senha: ACTIVEST_PASSWORD do ambiente ou do .env do projeto.
import { apiUrls, loadEnv, option } from './lib/env.mjs';

const args = process.argv.slice(2);
const ENV = loadEnv();
const { api: API } = apiUrls(ENV, args);
const EMAIL = option(args, '--email', ENV.ACTIVEST_EMAIL || 'guilhermeunatec@gmail.com');
const caminhos = args.filter((a, i) => a.startsWith('/') && !(i > 0 && args[i - 1].startsWith('--')));

if (!ENV.ACTIVEST_PASSWORD) {
  console.error('Defina ACTIVEST_PASSWORD (ambiente ou .env).');
  process.exit(2);
}
const login = await fetch(`${API}/api/v1/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: EMAIL, password: ENV.ACTIVEST_PASSWORD }),
});
if (!login.ok) {
  console.error(`Login recusado em ${API} (${login.status})`);
  process.exitCode = 1;
} else {
  const { accessToken } = await login.json();
  for (const caminho of caminhos) {
    const t0 = Date.now();
    const r = await fetch(`${API}${caminho}`, { headers: { Authorization: `Bearer ${accessToken}` } });
    console.log(`== ${caminho} -> ${r.status} (${Date.now() - t0} ms)`);
    console.log(await r.text());
  }
}
