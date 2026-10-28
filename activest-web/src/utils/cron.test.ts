import { describe, expect, it } from 'vitest';
import { CronForm, buildCron, describeCron, emptyCronForm, parseCron } from './cron';

const form = (over: Partial<CronForm>): CronForm => ({ ...emptyCronForm(), ...over });

describe('cron → formulário', () => {
  it('sem horário', () => {
    expect(parseCron('').mode).toBe('none');
    expect(parseCron(null).mode).toBe('none');
  });

  it('todo dia, um ou vários horários', () => {
    expect(parseCron('0 30 7 * * *')).toMatchObject({ mode: 'daily', times: '07:30' });
    expect(parseCron('0 30 8,21 * * *')).toMatchObject({ mode: 'daily', times: '08:30, 21:30' });
  });

  it('dias da semana, com faixa', () => {
    expect(parseCron('0 0 19 * * MON-FRI')).toMatchObject({ mode: 'weekly', days: ['MON', 'TUE', 'WED', 'THU', 'FRI'] });
    expect(parseCron('0 0 3 * * SUN')).toMatchObject({ mode: 'weekly', days: ['SUN'] });
  });

  it('mensal', () => {
    expect(parseCron('0 15 6 1 * *')).toMatchObject({ mode: 'monthly', dayOfMonth: 1, times: '06:15' });
  });

  it('o que não cabe nas formas simples vai para o avançado', () => {
    expect(parseCron('0 */5 * * * *').mode).toBe('advanced');
    expect(parseCron('30 0 7 * * *').mode).toBe('advanced');
    expect(parseCron('0 0 7 * 1 *').mode).toBe('advanced');
  });
});

describe('formulário → cron', () => {
  it('as quatro formas', () => {
    expect(buildCron(form({ mode: 'none' }))).toEqual({ cron: '' });
    expect(buildCron(form({ mode: 'daily', times: '21:30, 08:30' }))).toEqual({ cron: '0 30 8,21 * * *' });
    expect(buildCron(form({ mode: 'weekly', times: '19:00', days: ['FRI', 'MON'] }))).toEqual({ cron: '0 0 19 * * MON,FRI' });
    expect(buildCron(form({ mode: 'monthly', times: '06:15', dayOfMonth: 1 }))).toEqual({ cron: '0 15 6 1 * *' });
    expect(buildCron(form({ mode: 'advanced', raw: ' 0 */5 * * * * ' }))).toEqual({ cron: '0 */5 * * * *' });
  });

  it('erros', () => {
    expect(buildCron(form({ mode: 'daily', times: '25:00' }))).toHaveProperty('error');
    expect(buildCron(form({ mode: 'daily', times: '08:30, 21:00' }))).toHaveProperty('error');
    expect(buildCron(form({ mode: 'weekly', times: '08:30, 21:30', days: ['MON'] }))).toHaveProperty('error');
    expect(buildCron(form({ mode: 'weekly', times: '08:30', days: [] }))).toHaveProperty('error');
    expect(buildCron(form({ mode: 'monthly', times: '08:30', dayOfMonth: 32 }))).toHaveProperty('error');
    expect(buildCron(form({ mode: 'advanced', raw: '0 0 7 * *' }))).toHaveProperty('error');
  });

  it('ida e volta', () => {
    const cron = '0 0 19 * * MON-FRI';
    const back = buildCron(parseCron(cron));
    expect(back).toEqual({ cron: '0 0 19 * * MON,TUE,WED,THU,FRI' });
  });
});

describe('texto da tabela', () => {
  it('formas simples em português; o resto, o próprio cron', () => {
    expect(describeCron(null)).toBe('Sem horário');
    expect(describeCron('0 0 19 * * *')).toBe('Todo dia às 19:00');
    expect(describeCron('0 30 8,21 * * *')).toBe('Todo dia às 08:30 e 21:30');
    expect(describeCron('0 0 19 * * MON-FRI')).toBe('Seg a Sex às 19:00');
    expect(describeCron('0 0 3 * * SUN')).toBe('Dom às 03:00');
    expect(describeCron('0 0 3 * * MON,WED')).toBe('Seg, Qua às 03:00');
    expect(describeCron('0 40 7 15 * *')).toBe('Dia 15 às 07:40');
    expect(describeCron('0 */5 * * * *')).toBe('0 */5 * * * *');
  });
});
