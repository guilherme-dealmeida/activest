/**
 * Marcos de calendário no eixo X dos gráficos de linha — docs/planos/PLANO_CALENDARIO_GRAFICOS.md.
 *
 * Em vez de o Recharts escolher um ponto a cada N (datas "aleatórias" que mudam com a largura), o eixo marca
 * fronteiras do calendário pela duração do período (2.2): pregão, semana, quinzena, mês, trimestre, semestre, ano.
 * Se os marcos não couberem na largura, sobe um nível. Do mensal para cima o rótulo é só o mês (`nov/25`, D1) ou o ano
 * (`2024`, D4); a data exata fica no tooltip. Testado em `calendarTicks.test.ts`.
 */

export type Granularity = 'DAY' | 'WEEK' | 'FORTNIGHT' | 'MONTH' | 'QUARTER' | 'SEMESTER' | 'YEAR';

const ORDER: Granularity[] = ['DAY', 'WEEK', 'FORTNIGHT', 'MONTH', 'QUARTER', 'SEMESTER', 'YEAR'];
const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

interface Ymd { y: number; m: number; d: number }

/** "2026-11-07" (ou com hora) → ano, mês (1–12), dia; sem fuso: a data do texto é a data do ponto. */
const parse = (iso: string): Ymd => {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  return { y, m, d };
};

const pad = (n: number) => String(n).padStart(2, '0');
const toIso = ({ y, m, d }: Ymd) => `${y}-${pad(m)}-${pad(d)}`;
const dayNumber = ({ y, m, d }: Ymd) => Date.UTC(y, m - 1, d) / 86_400_000;
const fromDayNumber = (n: number): Ymd => {
  const dt = new Date(n * 86_400_000);
  return { y: dt.getUTCFullYear(), m: dt.getUTCMonth() + 1, d: dt.getUTCDate() };
};

/** Granularidade inicial pela duração do período, em dias (tabela 2.2 do plano). */
export const granularityFor = (days: number): Granularity =>
  days <= 7 ? 'DAY'
    : days <= 31 ? 'WEEK'
      : days <= 92 ? 'FORTNIGHT'
        : days <= 366 ? 'MONTH'
          : days <= 1096 ? 'QUARTER'
            : 'SEMESTER';

/** Início do intervalo de calendário que contém a data (a segunda-feira da semana, o dia 1 ou 16, o dia 1º...). */
const bucketStart = (p: Ymd, g: Granularity): Ymd => {
  switch (g) {
    case 'DAY': return p;
    case 'WEEK': {
      const n = dayNumber(p);
      const weekday = (new Date(n * 86_400_000).getUTCDay() + 6) % 7; // segunda = 0
      return fromDayNumber(n - weekday);
    }
    case 'FORTNIGHT': return { y: p.y, m: p.m, d: p.d < 16 ? 1 : 16 };
    case 'MONTH': return { y: p.y, m: p.m, d: 1 };
    case 'QUARTER': return { y: p.y, m: Math.floor((p.m - 1) / 3) * 3 + 1, d: 1 };
    case 'SEMESTER': return { y: p.y, m: p.m <= 6 ? 1 : 7, d: 1 };
    case 'YEAR': return { y: p.y, m: 1, d: 1 };
  }
};

/** O próximo início de intervalo depois de {@code start} (que já é um início). */
const nextBucket = (s: Ymd, g: Granularity): Ymd => {
  const addMonths = (k: number): Ymd => {
    const total = s.y * 12 + (s.m - 1) + k;
    return { y: Math.floor(total / 12), m: (total % 12) + 1, d: 1 };
  };
  switch (g) {
    case 'DAY': return fromDayNumber(dayNumber(s) + 1);
    case 'WEEK': return fromDayNumber(dayNumber(s) + 7);
    case 'FORTNIGHT': return s.d === 1 ? { ...s, d: 16 } : addMonths(1);
    case 'MONTH': return addMonths(1);
    case 'QUARTER': return addMonths(3);
    case 'SEMESTER': return addMonths(6);
    case 'YEAR': return { y: s.y + 1, m: 1, d: 1 };
  }
};

/** Rótulo do marco: dia/mês até a quinzena; `nov/25` do mensal ao semestral (D1); `2024` no anual (D4). */
export const formatTick = (iso: string, g: Granularity): string => {
  const p = parse(iso);
  if (g === 'YEAR') return String(p.y);
  if (g === 'DAY' || g === 'WEEK' || g === 'FORTNIGHT') return `${pad(p.d)}/${pad(p.m)}`;
  return `${MONTHS[p.m - 1]}/${String(p.y).slice(2)}`;
};

const sameDay = (a: Ymd, b: Ymd) => a.y === b.y && a.m === b.m && a.d === b.d;

/**
 * Marcos num eixo de CATEGORIA (os pontos da série): o primeiro ponto de cada intervalo do calendário. O primeiro
 * ponto da série só entra se ele mesmo abre o intervalo (ex.: série que começa no dia 1º) — senão o marco seria uma
 * data solta (D2).
 */
const categoryTicks = (dates: string[], g: Granularity): string[] => {
  const ticks: string[] = [];
  let previous: string | null = null;
  dates.forEach((iso, i) => {
    const p = parse(iso);
    const bucket = toIso(bucketStart(p, g));
    if (bucket !== previous && (i > 0 || sameDay(p, bucketStart(p, g)) || g === 'DAY')) {
      ticks.push(iso);
    }
    previous = bucket;
  });
  return ticks;
};

export interface CalendarAxis {
  ticks: string[];
  granularity: Granularity;
  format: (value: string) => string;
}

/**
 * Eixo de categoria: marcos e formato para uma série de datas ISO em ordem. {@code maxTicks} vem da largura do
 * gráfico; se os marcos não couberem, a granularidade sobe um nível até caber. {@code minGranularity}: o nível mais
 * fino aceito (MONTH nas séries mensais).
 */
export const calendarTicks = (dates: string[], maxTicks = Infinity, minGranularity: Granularity = 'DAY'): CalendarAxis => {
  if (dates.length === 0) {
    return { ticks: [], granularity: 'MONTH', format: v => formatTick(v, 'MONTH') };
  }
  const days = dayNumber(parse(dates[dates.length - 1])) - dayNumber(parse(dates[0]));
  // série mensal (IPCA, IGPM, INPC): nunca abaixo do mês — o índice não tem dia (C3)
  let index = Math.max(ORDER.indexOf(granularityFor(days)), ORDER.indexOf(minGranularity));
  let ticks = categoryTicks(dates, ORDER[index]);
  while (ticks.length > Math.max(maxTicks, 2) && index < ORDER.length - 1) {
    index += 1;
    ticks = categoryTicks(dates, ORDER[index]);
  }
  const granularity = ORDER[index];
  return { ticks, granularity, format: v => formatTick(String(v), granularity) };
};

/**
 * Eixo de TEMPO (números): os inícios de intervalo entre a primeira e a última data, exatamente no dia (G-6, o
 * Patrimônio da meta). {@code toValue} converte a data no valor do eixo (lá, o meio-dia local).
 */
export const calendarTimeTicks = (fromIso: string, toIso_: string, maxTicks: number,
                                  toValue: (iso: string) => number): { ticks: number[]; granularity: Granularity; format: (iso: string) => string } => {
  const from = parse(fromIso);
  const to = parse(toIso_);
  const days = dayNumber(to) - dayNumber(from);
  const build = (g: Granularity) => {
    const out: string[] = [];
    let s = bucketStart(from, g);
    if (dayNumber(s) < dayNumber(from)) s = nextBucket(s, g);
    while (dayNumber(s) <= dayNumber(to)) {
      out.push(toIso(s));
      s = nextBucket(s, g);
    }
    return out;
  };
  let index = ORDER.indexOf(granularityFor(days));
  let isoTicks = build(ORDER[index]);
  while (isoTicks.length > Math.max(maxTicks, 2) && index < ORDER.length - 1) {
    index += 1;
    isoTicks = build(ORDER[index]);
  }
  const granularity = ORDER[index];
  return { ticks: isoTicks.map(toValue), granularity, format: iso => formatTick(iso, granularity) };
};

/** Quantos rótulos cabem na largura do gráfico (~70 px por rótulo, descontado o eixo Y). */
export const maxTicksFor = (width: number) => (width > 0 ? Math.max(2, Math.floor((width - 60) / 70)) : Infinity);
