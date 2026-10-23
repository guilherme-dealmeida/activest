/**
 * Horário das tarefas (D10): o editor trabalha com quatro formas simples sobre o cron do Spring (6 campos:
 * segundo minuto hora dia-do-mês mês dia-da-semana) e cai no modo avançado (cron cru) para o resto.
 */

export type CronMode = 'none' | 'daily' | 'weekly' | 'monthly' | 'advanced';

export interface CronForm {
  mode: CronMode;
  /** "HH:MM"; no diário pode ter mais de um horário com o mesmo minuto ("08:30, 21:30"). */
  times: string;
  /** Dias da semana marcados (MON..SUN), no modo semanal. */
  days: string[];
  /** Dia do mês (1–31), no modo mensal. */
  dayOfMonth: number;
  /** Cron cru, no modo avançado. */
  raw: string;
}

export const WEEK_DAYS: { key: string; label: string; short: string }[] = [
  { key: 'MON', label: 'Segunda', short: 'Seg' },
  { key: 'TUE', label: 'Terça', short: 'Ter' },
  { key: 'WED', label: 'Quarta', short: 'Qua' },
  { key: 'THU', label: 'Quinta', short: 'Qui' },
  { key: 'FRI', label: 'Sexta', short: 'Sex' },
  { key: 'SAT', label: 'Sábado', short: 'Sáb' },
  { key: 'SUN', label: 'Domingo', short: 'Dom' },
];

const pad = (n: number) => String(n).padStart(2, '0');

/** "MON-FRI" e "MON,WED" viram a lista de dias. */
const expandDays = (field: string): string[] | null => {
  const keys = WEEK_DAYS.map(d => d.key);
  const out: string[] = [];
  for (const part of field.split(',')) {
    const range = part.split('-');
    if (range.length === 2) {
      const a = keys.indexOf(range[0]);
      const b = keys.indexOf(range[1]);
      if (a < 0 || b < 0 || b < a) return null;
      out.push(...keys.slice(a, b + 1));
    } else if (keys.includes(part)) {
      out.push(part);
    } else {
      return null;
    }
  }
  return out;
};

const isNum = (s: string) => /^\d+$/.test(s);

export const emptyCronForm = (): CronForm => ({ mode: 'none', times: '', days: [], dayOfMonth: 1, raw: '' });

/** Cron → formulário; o que não cabe nas formas simples fica no avançado. */
export const parseCron = (cron?: string | null): CronForm => {
  const form = emptyCronForm();
  if (!cron) return form;
  form.raw = cron;
  const f = cron.trim().split(/\s+/);
  if (f.length !== 6 || f[0] !== '0' || !isNum(f[1]) || f[4] !== '*') return { ...form, mode: 'advanced' };
  const hours = f[2].split(',');
  if (!hours.every(isNum)) return { ...form, mode: 'advanced' };
  const minute = Number(f[1]);
  form.times = hours.map(h => `${pad(Number(h))}:${pad(minute)}`).join(', ');
  if (f[3] === '*' && f[5] === '*') return { ...form, mode: 'daily' };
  if (f[3] === '*' && hours.length === 1) {
    const days = expandDays(f[5]);
    if (days) return { ...form, mode: 'weekly', days };
  }
  if (isNum(f[3]) && f[5] === '*' && hours.length === 1) return { ...form, mode: 'monthly', dayOfMonth: Number(f[3]) };
  return { ...form, mode: 'advanced' };
};

/** Horários "HH:MM, HH:MM" com o mesmo minuto → { minute, hours }; inválido → null. */
const parseTimes = (times: string): { minute: number; hours: number[] } | null => {
  const parts = times.split(',').map(t => t.trim()).filter(Boolean);
  if (parts.length === 0) return null;
  let minute: number | null = null;
  const hours: number[] = [];
  for (const p of parts) {
    const m = /^(\d{1,2}):(\d{2})$/.exec(p);
    if (!m) return null;
    const h = Number(m[1]);
    const min = Number(m[2]);
    if (h > 23 || min > 59) return null;
    if (minute !== null && minute !== min) return null;
    minute = min;
    hours.push(h);
  }
  return { minute: minute as number, hours: [...new Set(hours)].sort((a, b) => a - b) };
};

/** Formulário → cron ("" = sem horário) ou a mensagem do que está errado. */
export const buildCron = (form: CronForm): { cron: string } | { error: string } => {
  if (form.mode === 'none') return { cron: '' };
  if (form.mode === 'advanced') {
    const raw = form.raw.trim();
    return raw.split(/\s+/).length === 6 ? { cron: raw } : { error: 'O cron tem 6 campos: segundo minuto hora dia mês dia-da-semana.' };
  }
  const t = parseTimes(form.times);
  if (!t) return { error: 'Horário no formato HH:MM (vários horários só com o mesmo minuto, ex.: 08:30, 21:30).' };
  if (form.mode !== 'daily' && t.hours.length > 1) return { error: 'Um horário só neste modo.' };
  const hours = t.hours.join(',');
  if (form.mode === 'daily') return { cron: `0 ${t.minute} ${hours} * * *` };
  if (form.mode === 'weekly') {
    if (form.days.length === 0) return { error: 'Marque ao menos um dia da semana.' };
    const order = WEEK_DAYS.map(d => d.key);
    const days = [...form.days].sort((a, b) => order.indexOf(a) - order.indexOf(b)).join(',');
    return { cron: `0 ${t.minute} ${hours} * * ${days}` };
  }
  if (form.dayOfMonth < 1 || form.dayOfMonth > 31) return { error: 'Dia do mês de 1 a 31.' };
  return { cron: `0 ${t.minute} ${hours} ${form.dayOfMonth} * *` };
};

/** Texto do horário para a tabela ("Todo dia às 19:00", "Seg a Sex às 19:00"); fora das formas simples, o cron. */
export const describeCron = (cron?: string | null): string => {
  if (!cron) return 'Sem horário';
  const form = parseCron(cron);
  const times = form.times.split(', ').join(' e ');
  switch (form.mode) {
    case 'daily':
      return `Todo dia às ${times}`;
    case 'weekly': {
      const keys = WEEK_DAYS.map(d => d.key);
      const idx = form.days.map(d => keys.indexOf(d));
      const contiguous = idx.length > 2 && idx.every((v, i) => i === 0 || v === idx[i - 1] + 1);
      const label = contiguous
        ? `${WEEK_DAYS[idx[0]].short} a ${WEEK_DAYS[idx[idx.length - 1]].short}`
        : form.days.map(d => WEEK_DAYS[keys.indexOf(d)].short).join(', ');
      return `${label} às ${times}`;
    }
    case 'monthly':
      return `Dia ${form.dayOfMonth} às ${times}`;
    default:
      return cron;
  }
};
