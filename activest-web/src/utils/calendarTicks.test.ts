import { describe, expect, it } from 'vitest';
import { calendarTicks, calendarTimeTicks, formatTick, granularityFor, maxTicksFor } from './calendarTicks';

/** Pregões (seg–sex) de {@code from} até {@code to}, ISO. */
const businessDays = (from: string, to: string) => {
  const out: string[] = [];
  for (let d = new Date(`${from}T00:00:00Z`); d <= new Date(`${to}T00:00:00Z`); d.setUTCDate(d.getUTCDate() + 1)) {
    if (d.getUTCDay() !== 0 && d.getUTCDay() !== 6) out.push(d.toISOString().slice(0, 10));
  }
  return out;
};

describe('granularityFor (tabela 2.2)', () => {
  it('pela duração do período', () => {
    expect(granularityFor(5)).toBe('DAY');
    expect(granularityFor(30)).toBe('WEEK');
    expect(granularityFor(90)).toBe('FORTNIGHT');
    expect(granularityFor(365)).toBe('MONTH');
    expect(granularityFor(3 * 365)).toBe('QUARTER');
    expect(granularityFor(6 * 365)).toBe('SEMESTER');
  });
});

describe('calendarTicks (eixo de categoria)', () => {
  it('12 meses de pregões: o primeiro pregão de cada mês, rótulo nov/25 (C1)', () => {
    const axis = calendarTicks(businessDays('2025-11-10', '2026-11-06'));
    expect(axis.granularity).toBe('MONTH');
    // a série começa em 10/11: novembro não tem marco (seria data solta, D2); 01/12/2025 é segunda
    expect(axis.ticks[0]).toBe('2025-12-01');
    // 01/02/2026 é domingo: o marco é o primeiro pregão, 02/02
    expect(axis.ticks).toContain('2026-02-02');
    expect(axis.ticks).toHaveLength(12); // dez/25 a nov/26
    expect(axis.format('2026-02-02')).toBe('fev/26');
  });

  it('até 1 mês: semanal, na segunda (ou no primeiro pregão da semana)', () => {
    const axis = calendarTicks(businessDays('2026-10-08', '2026-11-06'));
    expect(axis.granularity).toBe('WEEK');
    expect(axis.ticks).toEqual(['2026-10-12', '2026-10-19', '2026-10-26', '2026-11-02']);
    expect(axis.format('2026-10-12')).toBe('12/10');
  });

  it('até 3 meses: quinzenal (dias 1 e 16 ou o pregão seguinte)', () => {
    const axis = calendarTicks(businessDays('2026-08-10', '2026-11-06'));
    expect(axis.granularity).toBe('FORTNIGHT');
    expect(axis.ticks.slice(0, 3)).toEqual(['2026-08-17', '2026-09-01', '2026-09-16']);
  });

  it('série que começa no dia 1º ganha o marco do começo', () => {
    expect(calendarTicks(businessDays('2026-06-01', '2026-11-06')).ticks[0]).toBe('2026-06-01');
  });

  it('sem espaço, sobe de nível em vez de pular rótulos (C4)', () => {
    const dates = businessDays('2025-11-10', '2026-11-06');
    const axis = calendarTicks(dates, 5);
    expect(axis.granularity).toBe('QUARTER');
    expect(axis.ticks).toEqual(['2026-01-01', '2026-04-01', '2026-07-01', '2026-10-01']);
  });

  it('série mensal (índice) longa: semestral e depois anual; virada de ano', () => {
    const months = Array.from({ length: 120 }, (_, i) => {
      const y = 2016 + Math.floor(i / 12); const m = (i % 12) + 1;
      return `${y}-${String(m).padStart(2, '0')}-01`;
    });
    const axis = calendarTicks(months);
    expect(axis.granularity).toBe('SEMESTER');
    expect(axis.ticks.slice(0, 3)).toEqual(['2016-01-01', '2016-07-01', '2017-01-01']);
    const narrow = calendarTicks(months, 12);
    expect(narrow.granularity).toBe('YEAR');
    expect(narrow.format('2017-01-01')).toBe('2017');
  });

  it('série amostrada por semana: o marco mensal é o primeiro ponto do mês, rotulado só com o mês', () => {
    const fridays = businessDays('2026-05-01', '2026-11-06').filter(d => new Date(`${d}T00:00:00Z`).getUTCDay() === 5);
    const axis = calendarTicks(fridays);
    expect(axis.granularity).toBe('MONTH');
    expect(axis.ticks.slice(-2)).toEqual(['2026-10-02', '2026-11-06']);
    expect(axis.format('2026-11-06')).toBe('nov/26');
  });

  it('série mensal curta: nunca abaixo do mês (C3)', () => {
    const axis = calendarTicks(['2026-08-01', '2026-09-01', '2026-10-01'], Infinity, 'MONTH');
    expect(axis.granularity).toBe('MONTH');
    expect(axis.format('2026-09-01')).toBe('set/26');
  });

  it('até 7 dias: cada pregão; série vazia não quebra', () => {
    expect(calendarTicks(businessDays('2026-11-02', '2026-11-06')).ticks).toHaveLength(5);
    expect(calendarTicks([]).ticks).toEqual([]);
  });
});

describe('calendarTimeTicks (eixo de tempo, Patrimônio da meta)', () => {
  const noon = (iso: string) => new Date(`${iso}T12:00:00`).getTime();

  it('marcos exatamente no dia 1º, sem pular meses (C2)', () => {
    const axis = calendarTimeTicks('2024-06-14', '2026-12-31', 20, noon);
    expect(axis.granularity).toBe('QUARTER');
    expect(axis.ticks[0]).toBe(noon('2024-07-01'));
    expect(axis.ticks[1]).toBe(noon('2024-10-01'));
    expect(axis.format('2024-07-01')).toBe('jul/24');
  });

  it('estreito: semestral', () => {
    expect(calendarTimeTicks('2024-06-14', '2026-12-31', 6, noon).granularity).toBe('SEMESTER');
  });
});

describe('formatTick e maxTicksFor', () => {
  it('formatos', () => {
    expect(formatTick('2026-01-05', 'DAY')).toBe('05/01');
    expect(formatTick('2026-07-01', 'SEMESTER')).toBe('jul/26');
  });

  it('largura → quantidade de marcos', () => {
    expect(maxTicksFor(0)).toBe(Infinity);
    expect(maxTicksFor(800)).toBe(10);
    expect(maxTicksFor(100)).toBe(2);
  });
});
