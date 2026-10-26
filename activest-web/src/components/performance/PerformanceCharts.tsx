import { useMemo, useState } from 'react';
import {
  Bar, BarChart, CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { useTheme } from '@/context/ThemeContext';
import { formatCurrency } from '@/utils/format';
import {
  BenchmarkResult, Granularity, PerformancePeriodic, PerformanceSeries, PerformanceWealth,
} from '@/types/performance';
import { date, pct, periodLabel, seriesColor } from './performanceFormat';

const CARTEIRA = 'CARTEIRA';

const useChartTheme = () => {
  const { isDark } = useTheme();
  return {
    isDark,
    grid: isDark ? '#2a2a2a' : '#e8e8e0',
    axis: '#777777',
    cursor: isDark ? '#ffffff0d' : '#0000000a',
  };
};

const axisDate = (iso: string, long: boolean) => {
  const [y, m, d] = iso.slice(0, 10).split('-');
  return long ? `${m}/${y.slice(2)}` : `${d}/${m}`;
};

/** Legenda acima do gráfico: cor, nome e o valor do período (rótulo direto — a cor nunca é a única pista). */
const SeriesLegend = ({ items, hidden, onToggle }: {
  items: { key: string; name: string; value?: string; color: string }[];
  hidden: Set<string>;
  onToggle: (key: string) => void;
}) => (
  <div className="mb-3 flex flex-wrap gap-x-4 gap-y-1.5 text-xs" role="group" aria-label="Séries">
    {items.map(it => (
      <button key={it.key} type="button" onClick={() => onToggle(it.key)} aria-pressed={!hidden.has(it.key)}
        className={`inline-flex items-center gap-1.5 ${hidden.has(it.key) ? 'opacity-40' : ''}`}>
        <span className="inline-block h-0.5 w-4 rounded" style={{ background: it.color }} aria-hidden="true" />
        <span className="text-gray-300">{it.name}</span>
        {it.value && <span className="font-semibold text-white">{it.value}</span>}
      </button>
    ))}
  </div>
);

interface TipRow { name: string; color: string; value: string }

const TipBox = ({ title, rows, note }: { title: string; rows: TipRow[]; note?: string }) => (
  <div className="rounded-md border border-white/10 bg-surface px-3 py-2 text-xs shadow-lg min-w-[180px]">
    <div className="mb-1 text-gray-400">{title}</div>
    {rows.map(r => (
      <div key={r.name} className="flex items-center justify-between gap-4">
        <span className="flex items-center gap-1.5 text-gray-300">
          <span className="inline-block h-2 w-2 rounded-full" style={{ background: r.color }} aria-hidden="true" />{r.name}
        </span>
        <span className="font-semibold text-white">{r.value}</span>
      </div>
    ))}
    {note && <div className="mt-1 text-[11px] text-gray-500">{note}</div>}
  </div>
);

// ------------------------------------------------------------------ acumulado

/** Rentabilidade acumulada da carteira e dos benchmarks, eixo compartilhado (E04 item 2). */
export const CumulativeChart = ({ data }: { data: PerformanceSeries }) => {
  const t = useChartTheme();
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const toggle = (k: string) => setHidden(prev => {
    const n = new Set(prev);
    if (n.has(k)) n.delete(k); else n.add(k);
    return n;
  });
  const benches = data.benchmarks;
  const rows = useMemo(() => data.points.map(p => ({
    date: p.date, [CARTEIRA]: p.portfolio, ...p.benchmarks,
    _flag: p.unavailable ? 'sem preço' : p.estimated ? 'IPCA do mês estimado na renda fixa' : p.stale ? 'algum ativo com o último preço conhecido' : '',
  })), [data]);
  const long = data.density !== 'DIARIA';
  const last = data.points.length ? data.points[data.points.length - 1] : null;
  const legend = [
    { key: CARTEIRA, name: 'Carteira', value: pct(last?.portfolio), color: seriesColor(CARTEIRA, t.isDark) },
    ...benches.map((b: BenchmarkResult) => ({
      key: b.code, name: b.name, value: pct(b.value) + (b.partial ? ` (até ${date(b.until)})` : ''),
      color: seriesColor(b.code, t.isDark),
    })),
  ];

  const Tip = ({ active, payload, label }: { active?: boolean; payload?: { dataKey: string; value: number | null; payload: { _flag: string } }[]; label?: string }) => {
    if (!active || !payload?.length || !label) return null;
    return (
      <TipBox title={date(label)} note={payload[0].payload._flag || undefined}
        rows={legend.filter(l => !hidden.has(l.key)).map(l => {
          const v = payload.find(x => x.dataKey === l.key)?.value;
          return { name: l.name, color: l.color, value: pct(v ?? null) };
        })} />
    );
  };

  return (
    <div>
      <SeriesLegend items={legend} hidden={hidden} onToggle={toggle} />
      <div className="h-80">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={rows} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
            <CartesianGrid stroke={t.grid} strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="date" tickFormatter={(v: string) => axisDate(v, long)} tick={{ fill: t.axis, fontSize: 11 }}
              tickLine={false} axisLine={{ stroke: t.grid }} minTickGap={32} />
            <YAxis tickFormatter={(v: number) => `${v.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`}
              tick={{ fill: t.axis, fontSize: 11 }} tickLine={false} axisLine={false} width={56} />
            <ReferenceLine y={0} stroke={t.axis} strokeOpacity={0.5} />
            <Tooltip content={<Tip />} cursor={{ stroke: t.axis, strokeDasharray: '3 3' }} />
            {legend.map(l => (
              <Line key={l.key} dataKey={l.key} type="linear" stroke={l.color} dot={false}
                strokeWidth={l.key === CARTEIRA ? 2.5 : 2} hide={hidden.has(l.key)} connectNulls={false}
                isAnimationActive={false} activeDot={{ r: 4, strokeWidth: 2, stroke: t.isDark ? '#111111' : '#ffffff' }} />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};

// ------------------------------------------------------------------ por período

const GRAN_LABEL: Record<Granularity, string> = { MONTH: 'Mensal', QUARTER: 'Trimestral', YEAR: 'Anual' };

/** Rentabilidade por mês, trimestre ou ano, carteira e benchmarks lado a lado (E04 item 3). */
export const PeriodicChart = ({ data, granularity, onGranularity }: {
  data: PerformancePeriodic; granularity: Granularity; onGranularity: (g: Granularity) => void;
}) => {
  const t = useChartTheme();
  const series = [{ key: CARTEIRA, name: 'Carteira' }, ...data.benchmarks.map(b => ({ key: b.code, name: b.name }))];
  const rows = data.rows.map(r => ({ key: r.key, partial: r.partial, [CARTEIRA]: r.portfolio, ...r.benchmarks }));

  const Tip = ({ active, payload }: { active?: boolean; payload?: { dataKey: string; value: number | null; payload: { key: string; partial: boolean } }[] }) => {
    if (!active || !payload?.length) return null;
    const row = payload[0].payload;
    return (
      <TipBox title={periodLabel(row.key)} note={row.partial ? 'Período incompleto' : undefined}
        rows={series.map(s => ({ name: s.name, color: seriesColor(s.key, t.isDark),
          value: pct(payload.find(x => x.dataKey === s.key)?.value ?? null) }))} />
    );
  };

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs">
          {series.map(s => (
            <span key={s.key} className="inline-flex items-center gap-1.5 text-gray-300">
              <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: seriesColor(s.key, t.isDark) }} aria-hidden="true" />
              {s.name}
            </span>
          ))}
        </div>
        <div className="flex gap-1" role="group" aria-label="Agrupamento">
          {(['MONTH', 'QUARTER', 'YEAR'] as Granularity[]).map(g => (
            <button key={g} type="button" onClick={() => onGranularity(g)} aria-pressed={granularity === g}
              className={`rounded-md px-2.5 py-1 text-xs font-medium ${granularity === g ? 'bg-accent text-black' : 'bg-white/5 text-gray-300 hover:bg-white/10'}`}>
              {GRAN_LABEL[g]}
            </button>
          ))}
        </div>
      </div>
      <div className="h-72">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barGap={2} barCategoryGap="20%">
            <CartesianGrid stroke={t.grid} strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="key" tickFormatter={periodLabel} tick={{ fill: t.axis, fontSize: 11 }} tickLine={false}
              axisLine={{ stroke: t.grid }} minTickGap={8} />
            <YAxis tickFormatter={(v: number) => `${v.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`}
              tick={{ fill: t.axis, fontSize: 11 }} tickLine={false} axisLine={false} width={56} />
            <ReferenceLine y={0} stroke={t.axis} />
            <Tooltip content={<Tip />} cursor={{ fill: t.cursor }} />
            {series.map(s => (
              <Bar key={s.key} dataKey={s.key} fill={seriesColor(s.key, t.isDark)} radius={[4, 4, 0, 0]} maxBarSize={18}
                isAnimationActive={false} />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};

// ------------------------------------------------------------------ patrimônio

const WEALTH = [
  { key: 'nominal', name: 'Patrimônio', color: (d: boolean) => seriesColor(CARTEIRA, d) },
  { key: 'real', name: 'Patrimônio corrigido pelo IPCA', color: (d: boolean) => seriesColor('CDI', d) },
  { key: 'invested', name: 'Investido (aportes − retiradas)', color: (d: boolean) => seriesColor('SELIC', d) },
  { key: 'investedReal', name: 'Investido corrigido pelo IPCA', color: (d: boolean) => seriesColor('IPCA', d) },
] as const;

/**
 * Patrimônio nominal e real contra o que foi investido (E06): a distância entre "patrimônio" e "investido" é o que os
 * investimentos renderam; entre "investido corrigido" e "patrimônio em reais de hoje", a preservação do poder de
 * compra. Os proventos saíram da carteira e aparecem à parte (no balão).
 */
export const WealthChart = ({ data }: { data: PerformanceWealth }) => {
  const t = useChartTheme();
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const toggle = (k: string) => setHidden(prev => {
    const n = new Set(prev);
    if (n.has(k)) n.delete(k); else n.add(k);
    return n;
  });
  const long = data.density !== 'DIARIA';
  const last = data.points.length ? data.points[data.points.length - 1] : null;
  const base = data.realBase ? ` (reais de ${data.realBase.slice(5, 7)}/${data.realBase.slice(0, 4)})` : '';
  const items = WEALTH.map(w => ({
    key: w.key, name: w.key === 'real' || w.key === 'investedReal' ? `${w.name}${base}` : w.name, color: w.color(t.isDark),
    value: last && last[w.key] != null ? formatCurrency(last[w.key] as number) : undefined,
  }));
  const Tip = ({ active, payload, label }: { active?: boolean; payload?: { dataKey: string; value: number | null; payload: { distributions: number; result: number | null } }[]; label?: string }) => {
    if (!active || !payload?.length || !label) return null;
    const p = payload[0].payload;
    return (
      <TipBox title={date(label)}
        note={`Proventos acumulados ${formatCurrency(p.distributions)} · resultado ${p.result != null ? formatCurrency(p.result) : '—'}`}
        rows={items.filter(i => !hidden.has(i.key)).map(i => {
          const v = payload.find(x => x.dataKey === i.key)?.value;
          return { name: i.name, color: i.color, value: v != null ? formatCurrency(v) : '—' };
        })} />
    );
  };
  return (
    <div>
      <SeriesLegend items={items} hidden={hidden} onToggle={toggle} />
      <div className="h-72">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data.points} margin={{ top: 8, right: 12, bottom: 0, left: 8 }}>
            <CartesianGrid stroke={t.grid} strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="date" tickFormatter={(v: string) => axisDate(v, long)} tick={{ fill: t.axis, fontSize: 11 }}
              tickLine={false} axisLine={{ stroke: t.grid }} minTickGap={32} />
            <YAxis tickFormatter={(v: number) => v.toLocaleString('pt-BR', { notation: 'compact', maximumFractionDigits: 1 })}
              tick={{ fill: t.axis, fontSize: 11 }} tickLine={false} axisLine={false} width={56} />
            <Tooltip content={<Tip />} cursor={{ stroke: t.axis, strokeDasharray: '3 3' }} />
            {items.map(i => (
              <Line key={i.key} dataKey={i.key} type="linear" stroke={i.color} dot={false} hide={hidden.has(i.key)}
                strokeWidth={i.key === 'nominal' ? 2.5 : 2} strokeDasharray={i.key.startsWith('invested') ? '5 4' : undefined}
                connectNulls={false} isAnimationActive={false} />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
