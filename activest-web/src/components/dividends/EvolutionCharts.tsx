import { useId } from 'react';
import { Bar, BarChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useTheme } from '@/context/ThemeContext';
import { formatCurrency } from '@/utils/format';
import { MONTH_SHORT, MonthRow, YearRow, monthLabel } from '@/utils/dividendPeriods';
import { ColoredSeries, toReceiveColors, toReceiveSwatch } from './evolutionColors';

/**
 * Gráficos da Evolução dos Proventos (docs/planos/PLANO_DIVIDENDOS_POR_PERIODO.md, blocos C e D): barras empilhadas
 * com o recebido por tipo de ativo (FIIs na base) e o a receber num bloco só, hachurado, no topo (D5).
 */

const TO_RECEIVE = 'toReceive';
const seriesKey = (typeKey: string) => `t_${typeKey}`;

const useChartTheme = () => {
  const { isDark } = useTheme();
  return {
    isDark,
    surface: isDark ? '#111111' : '#ffffff',
    grid: isDark ? '#2a2a2a' : '#e8e8e0',
    axis: isDark ? '#8a8a8a' : '#6b6b6b',
    label: isDark ? '#d1d5db' : '#374151',
    cursor: isDark ? '#ffffff0d' : '#0000000a',
  };
};

/** "R$ 1,2 mil", "R$ 250" — eixo e rótulos. */
const compactCurrency = (v: number) =>
  `R$ ${v.toLocaleString('pt-BR', { notation: 'compact', maximumFractionDigits: v >= 1000 ? 1 : 0 })}`;

interface ChartRow {
  key: string;
  /** Total, recebido e a receber em reais. */
  total: number;
  received: number;
  [TO_RECEIVE]: number;
  /** Série do segmento de cima (o que leva o canto arredondado e o rótulo). */
  _top: string | null;
  count: number;
  partial: boolean;
  growthPct?: number | null;
  [serie: string]: number | string | boolean | null | undefined;
}

const toRows = (items: (MonthRow | YearRow)[], types: ColoredSeries[], keyOf: (i: MonthRow | YearRow) => string,
  partialOf: (i: MonthRow | YearRow) => boolean): ChartRow[] =>
  items.map(it => {
    const row: ChartRow = {
      key: keyOf(it), total: it.totalCents / 100, received: it.receivedCents / 100, [TO_RECEIVE]: it.toReceiveCents / 100,
      _top: null, count: 'count' in it ? it.count : it.receivedCount + it.toReceiveCount, partial: partialOf(it),
      growthPct: 'growthPct' in it ? it.growthPct : undefined,
    };
    let top: string | null = null;
    types.forEach(t => {
      const cents = it.received[t.key] ?? 0;
      row[seriesKey(t.key)] = cents / 100;
      if (cents > 0) top = seriesKey(t.key);
    });
    if (it.toReceiveCents > 0) top = TO_RECEIVE;
    row._top = top;
    return row;
  });

/** Segmento da pilha: o de cima tem os cantos de 4 px arredondados e, no gráfico anual, o total em cima. */
const Segment = (props: {
  x?: number; y?: number; width?: number; height?: number; fill?: string; payload?: ChartRow;
  serie: string; surface: string; labelColor?: string;
}) => {
  const { x = 0, y = 0, width = 0, height = 0, fill, payload, serie, surface, labelColor } = props;
  if (!payload || height <= 0 || width <= 0) return null;
  const isTop = payload._top === serie;
  const r = isTop ? Math.min(4, height, width / 2) : 0;
  const d = r > 0
    ? `M${x},${y + height} L${x},${y + r} Q${x},${y} ${x + r},${y} L${x + width - r},${y} Q${x + width},${y} ${x + width},${y + r} L${x + width},${y + height} Z`
    : `M${x},${y + height} L${x},${y} L${x + width},${y} L${x + width},${y + height} Z`;
  return (
    <g>
      {/* contorno da cor da superfície = o vão de 2 px entre os segmentos */}
      <path d={d} fill={fill} stroke={surface} strokeWidth={2} />
      {isTop && labelColor && (
        <text x={x + width / 2} y={y - 8} textAnchor="middle" fontSize={12} fontWeight={600} fill={labelColor}>
          {formatCurrency(payload.total)}
        </text>
      )}
    </g>
  );
};

/** Legenda acima do gráfico: amostra + nome (a cor nunca é a única pista). */
export const EvolutionLegend = ({ types, hasToReceive }: { types: ColoredSeries[]; hasToReceive: boolean }) => {
  const { isDark } = useTheme();
  return (
    <div className="flex flex-wrap gap-x-4 gap-y-1.5 text-xs" role="list" aria-label="Legenda">
      {types.map(t => (
        <span key={t.key} role="listitem" className="inline-flex items-center gap-1.5 text-gray-300">
          <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: t.color }} aria-hidden="true" />
          {t.label}
        </span>
      ))}
      {hasToReceive && (
        <span role="listitem" className="inline-flex items-center gap-1.5 text-gray-300">
          <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: toReceiveSwatch(isDark) }} aria-hidden="true" />
          A receber
        </span>
      )}
    </div>
  );
};

type TipPayload = { payload: ChartRow }[];

const EvolutionTip = ({ active, payload, title, types, note }: {
  active?: boolean; payload?: TipPayload; title: (row: ChartRow) => string; types: ColoredSeries[];
  note?: (row: ChartRow) => string | undefined;
}) => {
  const { isDark } = useTheme();
  if (!active || !payload?.length) return null;
  const row = payload[0].payload;
  // de cima para baixo, como a pilha
  const parts = [
    ...(row[TO_RECEIVE] > 0 ? [{ name: 'A receber', swatch: toReceiveSwatch(isDark), value: row[TO_RECEIVE] }] : []),
    ...[...types].reverse()
      .map(t => ({ name: t.label, swatch: t.color, value: Number(row[seriesKey(t.key)] ?? 0) }))
      .filter(p => p.value > 0),
  ];
  const extra = note?.(row);
  return (
    <div className="rounded-md border border-white/10 bg-surface px-3 py-2 text-xs shadow-lg min-w-[200px]">
      <div className="mb-1 text-gray-400">{title(row)}</div>
      {parts.map(p => (
        <div key={p.name} className="flex items-center justify-between gap-4">
          <span className="flex items-center gap-1.5 text-gray-300">
            <span className="inline-block h-2 w-2 rounded-sm" style={{ background: p.swatch }} aria-hidden="true" />{p.name}
          </span>
          <span className="font-medium text-white tabular-nums">{formatCurrency(p.value)}</span>
        </div>
      ))}
      <div className="mt-1 flex items-center justify-between gap-4 border-t border-white/10 pt-1">
        <span className="text-gray-300">Total</span>
        <span className="font-semibold text-white tabular-nums">{formatCurrency(row.total)}</span>
      </div>
      <div className="mt-1 text-[11px] text-gray-500">
        {row.count} {row.count === 1 ? 'provento' : 'proventos'}{extra ? ` · ${extra}` : ''}
      </div>
    </div>
  );
};

/**
 * Barras de uma pilha: tipos de ativo na ordem (FIIs primeiro = base) e o a receber por último (topo). Função, e não
 * componente: o Recharts só enxerga `<Bar>` e `<defs>` que sejam filhos diretos do gráfico (um array serve).
 */
const stackBars = (types: ColoredSeries[], hatchId: string, barSize: number, surface: string, labelColor?: string) => [
  ...types.map(ty => (
    <Bar key={ty.key} dataKey={seriesKey(ty.key)} stackId="s" fill={ty.color} maxBarSize={barSize}
      isAnimationActive={false}
      shape={(p: object) => <Segment {...p} serie={seriesKey(ty.key)} surface={surface} labelColor={labelColor} />} />
  )),
  <Bar key={TO_RECEIVE} dataKey={TO_RECEIVE} stackId="s" fill={`url(#${hatchId})`} maxBarSize={barSize} isAnimationActive={false}
    shape={(p: object) => <Segment {...p} serie={TO_RECEIVE} surface={surface} labelColor={labelColor} />} />,
];

/** Hachura do a receber (padrão SVG), também filho direto do gráfico. */
const hatchDefs = (id: string, isDark: boolean) => {
  const { stripe, wash } = toReceiveColors(isDark);
  return (
    <defs>
      <pattern id={id} patternUnits="userSpaceOnUse" width={6} height={6} patternTransform="rotate(45)">
        <rect width={6} height={6} fill={wash} />
        <line x1={0} y1={0} x2={0} y2={6} stroke={stripe} strokeWidth={2.5} />
      </pattern>
    </defs>
  );
};

/** "jun/26" no eixo. */
const shortMonth = (key: string) => {
  const [y, m] = key.split('-');
  return `${MONTH_SHORT[Number(m) - 1]}/${y.slice(2)}`;
};

/** Bloco C: proventos por mês, com a linha da média e o clique que abre o detalhe do mês. */
export const MonthlyChart = ({ months, types, averageCents, onSelect }: {
  months: MonthRow[]; types: ColoredSeries[]; averageCents: number | null; onSelect: (key: string) => void;
}) => {
  const t = useChartTheme();
  const hatchId = `hatch-m-${useId().replace(/:/g, '')}`;
  const rows = toRows(months, types, i => (i as MonthRow).key, i => (i as MonthRow).current);
  const average = averageCents != null ? averageCents / 100 : null;
  return (
    <div className="h-72">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} margin={{ top: 12, right: 8, bottom: 0, left: 0 }} barCategoryGap="16%"
          onClick={(state) => { if (state?.activeLabel) onSelect(String(state.activeLabel)); }}
          style={{ cursor: 'pointer' }}>
          {hatchDefs(hatchId, t.isDark)}
          <CartesianGrid stroke={t.grid} vertical={false} />
          <XAxis dataKey="key" tickFormatter={shortMonth} tick={{ fill: t.axis, fontSize: 11 }} tickLine={false}
            axisLine={{ stroke: t.grid }} minTickGap={6} interval="preserveStartEnd" />
          <YAxis tickFormatter={compactCurrency} tick={{ fill: t.axis, fontSize: 11 }} tickLine={false} axisLine={false}
            width={80} />
          <Tooltip cursor={{ fill: t.cursor }} content={
            <EvolutionTip types={types} title={r => monthLabel(r.key)}
              note={r => [r.partial ? 'mês em andamento' : '', 'clique para ver o detalhe'].filter(Boolean).join(' · ')} />
          } />
          {stackBars(types, hatchId, 44, t.surface)}
          {average != null && average > 0 && (
            <ReferenceLine y={average} stroke={t.label} strokeOpacity={0.7} strokeDasharray="4 4"
              label={{ value: `média ${formatCurrency(average)}`, position: 'insideBottomRight', fill: t.label, fontSize: 11, fontWeight: 600 }} />
          )}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
};

/** Bloco D: proventos por ano, com o total em cima de cada barra. */
export const YearlyChart = ({ years, types }: { years: YearRow[]; types: ColoredSeries[] }) => {
  const t = useChartTheme();
  const hatchId = `hatch-y-${useId().replace(/:/g, '')}`;
  const rows = toRows(years, types, i => String((i as YearRow).year), i => (i as YearRow).partial);
  const pct = (v: number) => `${v > 0 ? '+' : ''}${v.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`;
  return (
    <div className="h-72">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} margin={{ top: 24, right: 8, bottom: 0, left: 0 }} barCategoryGap="24%">
          {hatchDefs(hatchId, t.isDark)}
          <CartesianGrid stroke={t.grid} vertical={false} />
          <XAxis dataKey="key" tick={{ fill: t.axis, fontSize: 11 }} tickLine={false} axisLine={{ stroke: t.grid }}
            tickFormatter={(k: string) => (rows.find(r => r.key === k)?.partial ? `${k} (parcial)` : k)} />
          <YAxis tickFormatter={compactCurrency} tick={{ fill: t.axis, fontSize: 11 }} tickLine={false} axisLine={false}
            width={80} />
          <Tooltip cursor={{ fill: t.cursor }} content={
            <EvolutionTip types={types} title={r => r.key}
              note={r => r.partial ? 'ano em andamento'
                : r.growthPct != null ? `${pct(r.growthPct)} sobre o ano anterior` : undefined} />
          } />
          {stackBars(types, hatchId, 72, t.surface, t.label)}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
};
