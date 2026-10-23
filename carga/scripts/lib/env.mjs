// Variaveis do projeto para os scripts: o ambiente do processo e o .env da raiz do repositorio.
// Sem caminho absoluto: a raiz e achada a partir deste arquivo (carga/scripts/lib/env.mjs -> ../../..).
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

function parse(file) {
  const out = {};
  if (!existsSync(file)) return out;
  for (const raw of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const m = raw.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/);
    if (!m) continue;
    out[m[1]] = m[2].replace(/^(['"])(.*)\1$/, '$2');
  }
  return out;
}

/** .env da raiz; o ambiente do processo vence. */
export function loadEnv() {
  const merged = parse(join(ROOT, '.env'));
  for (const [k, v] of Object.entries(process.env)) {
    if (v !== undefined && v !== '') merged[k] = v;
  }
  return merged;
}

/** Valor de --opcao nos argumentos, ou o padrao. */
export function option(args, name, fallback) {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith('--') ? args[i + 1] : fallback;
}

/** URLs das duas APIs: ACTIVEST_API_URL / ACTIVEST_ADM_URL, senao localhost nas portas do env. */
export function apiUrls(env, args = []) {
  const api = option(args, '--api', env.ACTIVEST_API_URL || `http://localhost:${env.API_PORT || 8080}`);
  const adm = option(args, '--adm', env.ACTIVEST_ADM_URL || `http://localhost:${env.ADM_API_PORT || 8081}`);
  return { api: api.replace(/\/$/, ''), adm: adm.replace(/\/$/, '') };
}
