import { assetText } from '@/utils/assetLabel';
import { useState } from 'react';
import clsx from 'clsx';
import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { useTheme } from '@/context/ThemeContext';
import { AssetCategory, AssetType } from '@/types';
import {
  BenchmarkCatalog, BreakdownRow, PerformanceBasis, PerformanceRange, ReservationFilter,
} from '@/types/performance';
import {
  BenchmarkPick, MAX_BENCHMARKS, RANGES, RANGE_LABEL, seriesColor,
} from './performanceFormat';

interface Props {
  types: AssetType[];
  categories: AssetCategory[];
  positions: BreakdownRow[];
  catalog?: BenchmarkCatalog;
  type: string;
  category: string;
  position: string;
  reservation: ReservationFilter;
  basis: PerformanceBasis;
  basisApplies: boolean;
  /** Carteira total ou tipo que pode ser reserva (renda fixa e Tesouro: asset_types.fixed_income). */
  showReservation: boolean;
  range: PerformanceRange;
  dateFrom: string;
  dateTo: string;
  firstDate?: string | null;
  lastDate?: string | null;
  benchmarks: BenchmarkPick[];
  onType: (v: string) => void;
  onCategory: (v: string) => void;
  onPosition: (v: string) => void;
  onReservation: (v: ReservationFilter) => void;
  onBasis: (v: PerformanceBasis) => void;
  onRange: (v: PerformanceRange) => void;
  onDates: (dateFrom: string, dateTo: string) => void;
  onBenchmarks: (v: BenchmarkPick[]) => void;
}

const selectClass = 'rounded-md border border-surface-light bg-surface px-2 py-1.5 text-sm text-white';
const segmentClass = (on: boolean) => `rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
  on ? 'bg-accent text-black' : 'bg-white/5 text-gray-300 hover:bg-white/10'}`;

/**
 * Filtros da Rentabilidade numa faixa só (docs/planos/PLANO_RENTABILIDADE.md, 6.1): 1ª linha o recorte (tipo →
 * categoria → ativo, L9); 2ª o período (D7, padrão 12M), a reserva (L8) e a base bruto/líquido (L10, só quando muda
 * algo; padrão bruto desde 17/10/2026); 3ª os benchmarks sempre expostos, ligados/desligados no clique (até 5; IPCA + x% com a taxa ao lado).
 */
export const PerformanceFilters = (p: Props) => {
  const { isDark } = useTheme();
  // IPCA + x%: enquanto o usuário não digita, o campo mostra a taxa do Tesouro IPCA+ à venda que vence primeiro (a
  // mesma do padrão do Tesouro Direto, 17/10/2026); sem taxa gravada, 6%. Depois de digitado, fica o que ele digitou.
  const [typedSpread, setTypedSpread] = useState<string | null>(null);
  const treasuryRate = Number(p.catalog?.treasuryIpca?.rate);
  const spread = typedSpread
    ?? (p.catalog?.treasuryIpca && Number.isFinite(treasuryRate) ? String(treasuryRate).replace('.', ',') : '6');
  const catalog = p.catalog?.benchmarks ?? [];
  const pickOf = (code: string) => p.benchmarks.find(b => b.code === code);
  const full = p.benchmarks.length >= MAX_BENCHMARKS;
  const parsedSpread = () => {
    const v = Number(spread.replace(',', '.'));
    return Number.isFinite(v) ? v : null;
  };

  // Índices sempre expostos (02/11/2026, como as flags da Carteira): clique liga/desliga. Ligar acrescenta no fim
  // (o primeiro continua o principal); o último ligado não desliga; com o limite cheio, os desligados ficam inativos.
  const toggle = (code: string) => {
    const current = pickOf(code);
    if (current) {
      if (p.benchmarks.length === 1) return;
      p.onBenchmarks(p.benchmarks.filter(b => b.code !== code));
      return;
    }
    if (full) return;
    const info = catalog.find(c => c.code === code);
    const pick: BenchmarkPick = { code };
    if (info?.acceptsSpread) {
      const v = parsedSpread();
      if (v == null) return;
      pick.spread = v;
    }
    p.onBenchmarks([...p.benchmarks, pick]);
  };

  // IPCA + x% ligado: a taxa digitada vale ao sair do campo (ou Enter), sem mudar a posição do índice na lista
  const applySpread = (code: string) => {
    const v = parsedSpread();
    if (v == null) return;
    p.onBenchmarks(p.benchmarks.map(b => (b.code === code ? { ...b, spread: v } : b)));
  };

  const nameOf = (b: BenchmarkPick) => {
    const info = catalog.find(c => c.code === b.code);
    if (!info) return b.code;
    return info.acceptsSpread && b.spread != null
      ? info.name.replace('x%', `${b.spread.toLocaleString('pt-BR')}%`) : info.name;
  };

  // IPCA + x% com a taxa do Tesouro IPCA+ à venda mais curto (padrão do Tesouro): de onde veio a taxa
  const ref = p.catalog?.treasuryIpca;
  const treasuryNote = (b: BenchmarkPick) => (ref && b.code === 'IPCA_MAIS' && b.spread === Number(ref.rate)
    ? `Taxa de compra do ${ref.ticker} em ${ref.rateDate.split('-').reverse().join('/')} (o Tesouro IPCA+ à venda que vence primeiro), aplicada ao período inteiro.`
    : undefined);

  return (
    <div className="bg-surface rounded-xl border border-surface-light p-4 space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <select aria-label="Tipo de ativo" value={p.type} onChange={e => p.onType(e.target.value)} className={selectClass}>
          <option value="">Carteira total</option>
          {p.types.map(t => <option key={t.id} value={t.name}>{t.description}</option>)}
        </select>
        <select aria-label="Categoria" value={p.category} onChange={e => p.onCategory(e.target.value)}
          className={selectClass} disabled={!p.type}>
          <option value="">Todas as categorias</option>
          {p.categories.map(c => <option key={c.id} value={String(c.id)}>{c.description || c.name}</option>)}
        </select>
        <select aria-label="Ativo" value={p.position} onChange={e => p.onPosition(e.target.value)} className={selectClass}>
          <option value="">Todos os ativos</option>
          {p.positions.map(r => (
            <option key={r.key} value={r.positionId ?? ''}>
              {assetText(r.label)}{r.detail && r.detail !== r.label ? ` — ${assetText(r.detail)}` : ''}{r.closed ? ' (encerrada)' : ''}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <div className="flex flex-wrap gap-1" role="group" aria-label="Período">
          {RANGES.map(r => (
            <button key={r} type="button" onClick={() => p.onRange(r)} aria-pressed={!p.dateFrom && !p.dateTo && p.range === r}
              className={segmentClass(!p.dateFrom && !p.dateTo && p.range === r)}>{RANGE_LABEL[r]}</button>
          ))}
        </div>
        <div className="flex items-center gap-1.5 text-xs text-gray-400">
          <label htmlFor="rent-de">de</label>
          <input id="rent-de" type="date" value={p.dateFrom} min={p.firstDate ?? undefined} max={p.lastDate ?? undefined}
            onChange={e => p.onDates(e.target.value, p.dateTo || (p.lastDate ?? ''))}
            className="rounded-md border border-surface-light bg-surface px-2 py-1 text-xs text-white" />
          <label htmlFor="rent-ate">até</label>
          <input id="rent-ate" type="date" value={p.dateTo} min={p.firstDate ?? undefined} max={p.lastDate ?? undefined}
            onChange={e => p.onDates(p.dateFrom || (p.firstDate ?? ''), e.target.value)}
            className="rounded-md border border-surface-light bg-surface px-2 py-1 text-xs text-white" />
        </div>
        {/* Reserva e base na linha do período (17/10/2026); Bruto primeiro, é o padrão */}
        {p.showReservation && <div className="flex gap-1" role="group" aria-label="Reserva de emergência">
          <button type="button" className={segmentClass(p.reservation === 'WITH')} aria-pressed={p.reservation === 'WITH'}
            onClick={() => p.onReservation('WITH')}>Com reserva</button>
          <button type="button" className={segmentClass(p.reservation === 'WITHOUT')} aria-pressed={p.reservation === 'WITHOUT'}
            onClick={() => p.onReservation('WITHOUT')}>Sem reserva</button>
        </div>}
        {p.basisApplies && (
          <div className="flex items-center gap-1" role="group" aria-label="Base de cálculo">
            <button type="button" className={segmentClass(p.basis === 'GROSS')} aria-pressed={p.basis === 'GROSS'}
              onClick={() => p.onBasis('GROSS')}>Bruto</button>
            <button type="button" className={segmentClass(p.basis === 'NET')} aria-pressed={p.basis === 'NET'}
              onClick={() => p.onBasis('NET')}>Líquido</button>
            <InfoTooltip content="Líquido: renda fixa e Tesouro pelo valor de resgate estimado (IR e IOF do dia), vendas pelo valor recebido e proventos sem o IR retido. Bruto: os mesmos valores antes dos impostos. Os índices (CDI, IPCA...) são sempre brutos — compare em base bruta para uma comparação equivalente." />
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs text-gray-400 flex items-center gap-1">
          Comparar com
          <InfoTooltip content={`Até ${MAX_BENCHMARKS} indicadores. O primeiro é o principal (cards e diferença). IPCA + x% usa a taxa real informada (% a.a.). O IVVB11 é um ETF que replica o S&P 500 em reais — uma aproximação, não a série oficial do índice.`} />
        </span>
        {catalog.map(info => {
          const pick = pickOf(info.code);
          const on = !!pick;
          const label = pick ? nameOf(pick) : info.name;
          const locked = on ? p.benchmarks.length === 1 : full;
          return (
            <span key={info.code} className="inline-flex items-center gap-1">
              <button type="button" aria-pressed={on} disabled={locked} onClick={() => toggle(info.code)}
                title={(pick && treasuryNote(pick)) || (locked
                  ? (on ? 'Ao menos um índice precisa ficar selecionado' : `Até ${MAX_BENCHMARKS} índices`)
                  : (on ? `Remover ${label}` : `Comparar com ${label}`))}
                className={clsx(
                  'inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium transition-colors disabled:cursor-not-allowed',
                  on
                    ? 'border-accent/40 bg-accent/15 text-accent hover:bg-accent/25'
                    : 'border-white/10 bg-surface-light/40 text-gray-500 hover:text-gray-300 disabled:hover:text-gray-500',
                )}>
                <span className={clsx('inline-block h-2 w-2 rounded-full', !on && 'opacity-40')}
                  style={{ background: seriesColor(info.code, isDark) }} aria-hidden="true" />
                {label}
              </button>
              {info.acceptsSpread && (
                <input aria-label="Taxa real (% a.a.)" value={pick?.spread != null && typedSpread == null
                  ? String(pick.spread).replace('.', ',') : spread}
                  disabled={!on} title={on ? undefined : `Ligue o ${info.name} para informar a taxa`}
                  onChange={e => setTypedSpread(e.target.value)}
                  onBlur={() => on && typedSpread != null && applySpread(info.code)}
                  onKeyDown={e => { if (e.key === 'Enter' && on) applySpread(info.code); }}
                  className="w-14 rounded-md border border-surface-light bg-surface px-2 py-1 text-xs text-white disabled:cursor-not-allowed disabled:opacity-40" />
              )}
            </span>
          );
        })}
      </div>
    </div>
  );
};
