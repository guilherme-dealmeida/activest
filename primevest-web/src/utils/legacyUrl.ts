/**
 * Endereços antigos, em português (docs/planos/PLANO_NOMES_EM_INGLES.md, E4): rotas e parâmetros de URL passaram a ser
 * em inglês em 22/10/2026. Link salvo, histórico do navegador e notificação gravada continuam valendo: a rota antiga
 * redireciona e os parâmetros antigos são traduzidos na chegada, sem prazo (N2). Os valores que são do domínio
 * (ticker, período `1A`/`5A`, códigos de benchmark) ficam como estão.
 */

const LEGACY_PATHS: [RegExp, string][] = [
  [/^\/rentabilidade(?=\/|$)/, '/performance'],
  [/^\/ativos(?=\/|$)/, '/assets'],
  [/^\/emissoras(?=\/|$)/, '/assets'],
  [/^\/cadastro(?=\/|$)/, '/signup'],
  [/^\/esqueci-senha(?=\/|$)/, '/forgot-password'],
  [/^\/termos(?=\/|$)/, '/terms'],
  [/^\/privacidade(?=\/|$)/, '/privacy'],
];

/** Parâmetro antigo → novo, por tela; `values` traduz os valores fixos que só existem na URL. */
interface ParamRule { to: string; values?: Record<string, string> }

const PERFORMANCE: Record<string, ParamRule> = {
  tipo: { to: 'type' },
  categoria: { to: 'category' },
  ativo: { to: 'position' },
  reserva: { to: 'reservation', values: { sem: 'without' } },
  base: { to: 'basis', values: { liquido: 'net' } },
  periodo: { to: 'range' },
  de: { to: 'from' },
  ate: { to: 'to' },
  indices: { to: 'benchmarks' },
};

const ASSETS: Record<string, ParamRule> = {
  aba: { to: 'tab', values: { acoes: 'stocks' } },
  periodo: { to: 'range' },
  de: { to: 'from' },
  ate: { to: 'to' },
};

/** Estado de cada aba da listagem de ativos: `acoes.ord=...` → `stocks.sort=...`. */
const LISTING_TABS: Record<string, string> = { acoes: 'stocks', fiis: 'fiis' };
const LISTING_KEYS: Record<string, string> = {
  ord: 'sort', pag: 'page', qtd: 'size', nome: 'name', setor: 'sector', subsetor: 'subsector', segmento: 'segment',
  preco: 'price', patrimonio: 'equity',
};
/** Valor de `ord` é `coluna.direção`: a coluna também muda de nome. */
const SORT_KEYS: Record<string, string> = { nome: 'name', setor: 'sector', preco: 'price', patrimonio: 'equity' };

const BY_PATH: [RegExp, Record<string, ParamRule>][] = [
  [/^\/performance$/, PERFORMANCE],
  [/^\/assets(\/|$)/, ASSETS],
  [/^\/dividends$/, { aba: { to: 'tab', values: { 'a-receber': 'to-receive' } } }],
  [/^\/strategy$/, { aba: { to: 'tab', values: { reserva: 'reservation' } } }],
];

const translatePath = (pathname: string): string => {
  for (const [re, to] of LEGACY_PATHS) {
    if (re.test(pathname)) return pathname.replace(re, to);
  }
  return pathname;
};

const translateParams = (pathname: string, search: string): string => {
  const rules = BY_PATH.find(([re]) => re.test(pathname))?.[1];
  const from = new URLSearchParams(search);
  if (!rules) return from.toString();
  const out = new URLSearchParams();
  from.forEach((value, key) => {
    const rule = rules[key];
    if (rule) {
      // o nome novo, se já veio na URL, vale mais que o antigo
      if (!from.has(rule.to)) out.set(rule.to, rule.values?.[value] ?? value);
      return;
    }
    const dot = key.indexOf('.');
    if (rules === ASSETS && dot > 0) {
      const tab = LISTING_TABS[key.slice(0, dot)];
      const sub = key.slice(dot + 1);
      if (tab) {
        const newSub = LISTING_KEYS[sub] ?? sub;
        let v = value;
        if (newSub === 'sort') {
          const [col, dir] = value.split('.');
          v = `${SORT_KEYS[col] ?? col}${dir ? `.${dir}` : ''}`;
        }
        out.set(`${tab}.${newSub}`, v);
        return;
      }
    }
    out.set(key, value);
  });
  return out.toString();
};

/**
 * O endereço em inglês equivalente a `pathname` + `search` (com ou sem `?`). Igual ao de entrada quando já é o
 * endereço novo.
 */
export const translateLegacyUrl = (pathname: string, search: string): string => {
  const path = translatePath(pathname);
  const query = translateParams(path, search.startsWith('?') ? search.slice(1) : search);
  return query ? `${path}?${query}` : path;
};

/** Se o endereço é antigo: rota ou parâmetro em português. */
export const isLegacyUrl = (pathname: string, search: string): boolean => {
  const current = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search).toString();
  return translateLegacyUrl(pathname, search) !== (current ? `${pathname}?${current}` : pathname);
};
