import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { indicesApi } from '@/api/indices';
import { IndexChartCard } from '@/components/indices/IndexChartCard';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { useTheme } from '@/context/ThemeContext';
import { YieldCurveCode, YieldCurveKind } from '@/types';
import { formatDate } from '@/utils/format';
import { apiMessage } from '@/utils/apiError';
import { rem } from '@/utils/fontScale';

const GOLD = '#c9a84c';

/** Prazos de cada curva (V24, decisões I2 de 11/11/2026). */
const YEARS: Record<YieldCurveKind, number[]> = {
  PRE: [1, 2, 3, 5, 8],
  REAL: [4, 5, 10, 20],
};
/** Prazo com que o card abre (P3). */
const DEFAULT_YEARS = 5;

const TITLE: Record<YieldCurveKind, string> = {
  PRE: 'Juros prefixados (Tesouro)',
  REAL: 'Juros reais (Tesouro IPCA+)',
};

const COMMON = 'Os títulos com juros semestrais entram como aproximação. Quando nenhum título do dia alcança o prazo, '
  + 'o trecho fica sem linha (a curva não é extrapolada). É uma taxa, não um rendimento: não há acumulado.';

const INFO: Record<YieldCurveKind, string> = {
  PRE: 'Taxa ao ano que o mercado pede hoje para emprestar ao governo por cada prazo, sem correção (juro nominal). '
    + 'Calculada com as taxas dos títulos Tesouro Prefixado do Tesouro Direto (média de compra e venda), interpoladas '
    + 'em dias úteis; o começo da curva é o CDI do dia. É a curva dos títulos públicos, não o DI futuro da B3, mas as '
    + 'duas andam juntas. ' + COMMON,
  REAL: 'Taxa ao ano que o mercado pede acima da inflação (IPCA) para cada prazo: é o "+ x%" do IPCA + x%. '
    + 'Calculada com as taxas dos títulos Tesouro IPCA+ do Tesouro Direto (média de compra e venda), interpoladas em '
    + 'dias úteis. ' + COMMON,
};

const LINES = [
  { key: 'd0', label: 'hoje' },
  { key: 'd1', label: '1 mês atrás' },
  { key: 'd2', label: '1 ano atrás' },
] as const;

const pct = (v: number) => `${v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}% a.a.`;
const yearsLabel = (y: number) => `${y} ${y === 1 ? 'ano' : 'anos'}`;

/** Curva do dia: taxa × prazo, com hoje, 1 mês e 1 ano atrás. */
const CurveOfDay = ({ curve }: { curve: YieldCurveKind }) => {
  const { isDark } = useTheme();
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['indices', 'curve', curve],
    queryFn: () => indicesApi.curve(curve),
  });
  const grid = isDark ? '#2a2a2a' : '#e8e8e0';
  const axis = '#777777';
  const colors = [GOLD, isDark ? '#9ca3af' : '#6b7280', isDark ? '#6b7280' : '#a3a3a3'];

  const rows = useMemo(() => (data?.vertices ?? []).map((v, i) => {
    const row: Record<string, number | null> = { years: v.years };
    data!.dates.forEach((d, j) => { row[`d${j}`] = d.rates[i] ?? null; });
    return row;
  }), [data]);
  const hasAny = rows.some(r => LINES.some(l => r[l.key] != null));

  const ChartTooltip = ({ active, payload }: { active?: boolean; payload?: { payload: Record<string, number | null> }[] }) => {
    if (!active || !payload?.length || !data) return null;
    const r = payload[0].payload;
    return (
      <div className="rounded-md border border-white/10 bg-surface px-3 py-2 text-xs shadow-lg">
        <div className="text-gray-400">{yearsLabel(r.years as number)}</div>
        {LINES.map((l, j) => {
          const d = data.dates[j];
          if (!d) return null;
          const v = r[l.key];
          return (
            <div key={l.key} className="mt-0.5 flex items-center gap-2">
              <span className="inline-block h-2 w-2 rounded-full" style={{ background: colors[j] }} />
              <span className="text-gray-400">{d.date ? formatDate(d.date) : l.label}</span>
              <span className="font-semibold text-white">{v != null ? pct(v) : '—'}</span>
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <div className="mt-6 border-t border-white/10 pt-4">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h4 className="text-sm font-medium text-gray-400">Curva do dia</h4>
        {data && (
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-400">
            {LINES.map((l, j) => data.dates[j] && (
              <span key={l.key} className="flex items-center gap-1.5">
                <span className="inline-block h-0.5 w-4" style={{ background: colors[j] }} />
                {l.label}{data.dates[j].date ? ` (${formatDate(data.dates[j].date!)})` : ' (sem dado)'}
              </span>
            ))}
          </div>
        )}
      </div>
      <div className="h-56">
        {isLoading ? (
          <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-6 w-6 text-accent" /></div>
        ) : isError ? (
          <div className="flex h-full items-center justify-center text-sm text-gray-400">
            {apiMessage(error) ?? 'Não foi possível carregar a curva.'}
          </div>
        ) : !hasAny ? (
          <div className="flex h-full items-center justify-center text-sm text-gray-400">Curva ainda não carregada.</div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={rows} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
              <CartesianGrid stroke={grid} strokeDasharray="3 3" vertical={false} />
              <XAxis
                dataKey="years" type="number" domain={[YEARS[curve][0] - 1, 'dataMax']} ticks={YEARS[curve]}
                tickFormatter={(y: number) => `${y}a`}
                tick={{ fill: axis, fontSize: rem(11) }} tickLine={false} axisLine={{ stroke: grid }}
              />
              <YAxis
                domain={['auto', 'auto']}
                tickFormatter={(v: number) => v.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}
                tick={{ fill: axis, fontSize: rem(11) }} tickLine={false} axisLine={false} width={48}
              />
              <Tooltip content={<ChartTooltip />} cursor={{ stroke: axis, strokeDasharray: '3 3' }} />
              {LINES.map((l, j) => (
                <Line
                  key={l.key} dataKey={l.key} type="monotone" stroke={colors[j]}
                  strokeWidth={j === 0 ? 2.5 : 1.5} strokeDasharray={j === 0 ? undefined : j === 1 ? '6 4' : '2 4'}
                  dot={{ r: j === 0 ? 3.5 : 2.5, fill: colors[j], strokeWidth: 0 }} connectNulls={false}
                  isAnimationActive={false}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
};

/**
 * Card de uma curva de juros do Tesouro na tela Índices (docs/planos/PLANO_JUROS_FUTUROS.md, J4/D8): seletor de
 * prazo, o gráfico do histórico do prazo (o mesmo do card de índice, que guarda o período ao trocar de prazo) e a
 * curva do dia.
 */
export const YieldCurveCard = ({ curve }: { curve: YieldCurveKind }) => {
  const [years, setYears] = useState<number>(DEFAULT_YEARS);
  const code = `JUROS_${curve}_${years}A` as YieldCurveCode;
  const prazo = (
    <div className="flex flex-wrap gap-1" role="group" aria-label={`Prazo de ${TITLE[curve]}`}>
      {YEARS[curve].map(y => (
        <button
          key={y}
          type="button"
          onClick={() => setYears(y)}
          aria-pressed={years === y}
          title={yearsLabel(y)}
          className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
            years === y ? 'bg-accent text-black' : 'border border-white/10 text-gray-300 hover:bg-white/10'
          }`}
        >
          {y}a
        </button>
      ))}
    </div>
  );
  return (
    <IndexChartCard
      code={code}
      title={`${TITLE[curve]} — ${yearsLabel(years)}`}
      info={INFO[curve]}
      controls={prazo}
      footer={<CurveOfDay curve={curve} />}
    />
  );
};
