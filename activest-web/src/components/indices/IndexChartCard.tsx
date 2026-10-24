import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { indicesApi } from '@/api/indices';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { useTheme } from '@/context/ThemeContext';
import { IndexChart, IndexCode, IndexView, PriceRange } from '@/types';
import { formatDate, getReturnColor } from '@/utils/format';
import { apiMessage } from '@/utils/apiError';

const RANGES: PriceRange[] = ['1D', '7D', '1M', '6M', 'YTD', '1A', '5A', 'MAX'];
/** Índices mensais: sem 1D e 7D (um mês é o menor período que eles têm). */
const MONTHLY: IndexCode[] = ['IPCA', 'IGPM', 'INPC'];
const MONTHLY_RANGES = RANGES.filter(r => r !== '1D' && r !== '7D');
const DEFAULT_RANGE: PriceRange = '1A';
const GOLD = '#c9a84c';

/** O que cada card explica no (i): fonte, unidade e o que o resumo mostra. */
const INFO: Record<IndexCode, string> = {
  SELIC: 'Selic efetiva (over), publicada pelo Banco Central (SGS 11) a cada dia útil. O gráfico mostra a taxa anualizada em 252 dias úteis; o acumulado compõe a taxa de cada dia do período.',
  CDI: 'Taxa DI (CDI), publicada pelo Banco Central (SGS 12) a cada dia útil. O gráfico mostra a taxa anualizada em 252 dias úteis; o acumulado compõe a taxa de cada dia do período — é a base do "% do CDI" da renda fixa.',
  IPCA: 'Inflação oficial (IBGE), variação mensal pelo Banco Central (SGS 433), divulgada por volta do dia 10 do mês seguinte. Visão anual: cada mês mostra o acumulado dos 12 meses terminados nele (% a.a.). O acumulado do resumo compõe os meses inteiros do período.',
  IBOV: 'Ibovespa, fechamento diário em pontos, pelo site da B3. A variação vai do primeiro ao último pregão do período.',
  IFIX: 'IFIX, índice dos fundos imobiliários, fechamento diário em pontos, pelo site da B3 (desde 30/12/2010). A variação vai do primeiro ao último pregão do período.',
  IGPM: 'IGP-M (FGV), variação mensal pelo Banco Central (SGS 189), divulgada no fim do mês de referência. Visão anual: cada mês mostra o acumulado dos 12 meses terminados nele (% a.a.). O acumulado do resumo compõe os meses inteiros do período.',
  INPC: 'INPC (IBGE), variação mensal pelo Banco Central (SGS 188), divulgada junto com o IPCA. Visão anual: cada mês mostra o acumulado dos 12 meses terminados nele (% a.a.). O acumulado do resumo compõe os meses inteiros do período.',
  TR: 'Taxa Referencial (SGS 226): cada valor é a taxa do período de um mês que começa no dia (rende a poupança com aniversário naquele dia). Os períodos se sobrepõem, por isso não há acumulado.',
  SELIC_META: 'Meta da Selic definida pelo Copom (SGS 432), em % ao ano, em vigor em cada dia. É uma meta, não um rendimento: não há acumulado.',
  USD_PTAX: 'Dólar PTAX de venda, cotação oficial do Banco Central (SGS 1). A variação vai do primeiro ao último dia útil do período.',
};

const num = (v: number, digits: number) =>
  v.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });

/** Valor no formato da unidade exibida. */
const formatValue = (c: Pick<IndexChart, 'unit' | 'displayUnit'>, v: number) => {
  switch (c.unit) {
    case 'PONTOS': return `${num(v, 2)} pts`;
    case 'BRL': return `${v < 0 ? '-' : ''}R$ ${num(Math.abs(v), 4)}`;
    case 'PCT_PERIODO': return `${num(v, 4)}%`;
    default: return `${num(v, 2)}${c.displayUnit}`;
  }
};

const signed = (v: number, text: string) => `${v > 0 ? '+' : ''}${text}`;

const axisDate = (iso: string, monthly: boolean, long: boolean) => {
  const [y, m, d] = iso.slice(0, 10).split('-');
  return monthly || long ? `${m}/${y}` : `${d}/${m}`;
};

const tooltipDate = (iso: string, monthly: boolean) => {
  if (!monthly) return formatDate(iso.slice(0, 10));
  const [y, m] = iso.slice(0, 10).split('-');
  return `${m}/${y}`;
};

/**
 * Card de um índice na tela Índices — o mesmo desenho do gráfico de cotação de Ativos (atalhos 1D…MAX, 1A
 * padrão, período de/até; nos mensais sem 1D e 7D). Diário em área (meta Selic em degraus); mensal em barras, porque a variação do mês pode
 * ser negativa. O período fica no card: cada índice tem o seu.
 */
export const IndexChartCard = ({ code }: { code: IndexCode }) => {
  const { isDark } = useTheme();
  const [range, setRangeState] = useState<PriceRange>(DEFAULT_RANGE);
  const [dateFrom, setDateFrom] = useState<string>('');
  const [dateTo, setDateTo] = useState<string>('');
  const [view, setView] = useState<IndexView>('MENSAL');
  const isMonthlyIndex = MONTHLY.includes(code);
  const custom = !!dateFrom || !!dateTo;
  const period = custom ? { from: dateFrom || undefined, to: dateTo || undefined } : { range };

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['indices', 'chart', code, custom ? `${dateFrom}_${dateTo}` : range, isMonthlyIndex ? view : ''],
    queryFn: () => indicesApi.chart(code, period, isMonthlyIndex ? view : undefined),
    placeholderData: (prev) => prev,
  });

  const setRange = (r: PriceRange) => {
    setDateFrom(''); setDateTo('');
    setRangeState(r);
  };

  const setDate = (key: 'from' | 'to', value: string) => {
    // o outro extremo começa no intervalo mostrado agora, para o período não "pular"
    if (key === 'from') {
      setDateFrom(value);
      if (!dateTo && data?.to) setDateTo(data.to);
    } else {
      setDateTo(value);
      if (!dateFrom && data?.from) setDateFrom(data.from);
    }
  };

  const monthly = isMonthlyIndex || data?.periodicity === 'MENSAL';
  const ranges = monthly ? MONTHLY_RANGES : RANGES;
  // visão anual: o acumulado em 12 meses é uma curva contínua (área); a mensal fica em barras
  const bars = monthly && view === 'MENSAL';
  const longAxis = useMemo(() => {
    if (!data?.from || !data?.to) return false;
    return (new Date(data.to).getTime() - new Date(data.from).getTime()) / 86_400_000 > 400;
  }, [data]);

  const grid = isDark ? '#2a2a2a' : '#e8e8e0';
  const axis = '#777777';
  const points = data?.points ?? [];
  // o nome do cadastro já diz o índice ("CDI — Taxa DI", "Meta Selic (Copom)"); o código só enquanto carrega
  const title = data?.name ?? code;
  const fillId = `indice-${code}`;

  const ChartTooltip = ({ active, payload }: { active?: boolean; payload?: { payload: { date: string; value: number } }[] }) => {
    if (!active || !payload?.length || !data) return null;
    const p = payload[0].payload;
    return (
      <div className="rounded-md border border-white/10 bg-surface px-3 py-2 text-xs shadow-lg">
        <div className="text-gray-400">{tooltipDate(p.date, monthly)}</div>
        <div className="mt-0.5 text-sm font-semibold text-white">{formatValue(data, p.value)}</div>
      </div>
    );
  };

  const xAxis = (
    <XAxis
      dataKey="date"
      tickFormatter={(v: string) => axisDate(v, monthly, longAxis)}
      tick={{ fill: axis, fontSize: 11 }}
      tickLine={false}
      axisLine={{ stroke: grid }}
      minTickGap={32}
    />
  );
  const yAxis = (
    <YAxis
      domain={monthly ? [(min: number) => Math.min(0, min), 'auto'] : ['auto', 'auto']}
      tickFormatter={(v: number) => v.toLocaleString('pt-BR', { maximumFractionDigits: data?.unit === 'PCT_PERIODO' ? 4 : 2 })}
      tick={{ fill: axis, fontSize: 11 }}
      tickLine={false}
      axisLine={false}
      width={64}
    />
  );

  return (
    <Card>
      <div className="flex flex-col gap-3 mb-4 lg:flex-row lg:items-center lg:justify-between">
        <h3 className="text-sm font-medium text-gray-400 flex items-center gap-1.5">
          {title}
          <InfoTooltip content={INFO[code]} />
        </h3>
        <div className="flex flex-wrap items-center gap-2">
          {isMonthlyIndex && (
            <select
              aria-label={`Visão de ${code}`}
              value={view}
              onChange={(e) => setView(e.target.value as IndexView)}
              className="rounded-md border border-surface-light bg-surface px-2 py-1 text-xs text-white"
            >
              <option value="MENSAL">Mensal (% no mês)</option>
              <option value="ANUAL">Anual (% a.a., 12 meses)</option>
            </select>
          )}
          <div className="flex flex-wrap gap-1" role="group" aria-label={`Período de ${code}`}>
            {ranges.map(r => (
              <button
                key={r}
                type="button"
                onClick={() => setRange(r)}
                aria-pressed={!custom && range === r}
                className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                  !custom && range === r
                    ? 'bg-accent text-black'
                    : 'bg-white/5 text-gray-300 hover:bg-white/10'
                }`}
              >
                {r}
              </button>
            ))}
          </div>
          <div className="flex items-center gap-1.5 text-xs text-gray-400">
            <label htmlFor={`${code}-de`}>de</label>
            <input
              id={`${code}-de`} type="date"
              value={custom ? (dateFrom || data?.from || '') : ''}
              min={data?.firstDate ?? undefined} max={data?.lastDate ?? undefined}
              onChange={(e) => setDate('from', e.target.value)}
              className="rounded-md border border-surface-light bg-surface px-2 py-1 text-xs text-white"
            />
            <label htmlFor={`${code}-ate`}>até</label>
            <input
              id={`${code}-ate`} type="date"
              value={custom ? (dateTo || data?.to || '') : ''}
              min={data?.firstDate ?? undefined} max={data?.lastDate ?? undefined}
              onChange={(e) => setDate('to', e.target.value)}
              className="rounded-md border border-surface-light bg-surface px-2 py-1 text-xs text-white"
            />
          </div>
        </div>
      </div>

      {data && points.length > 0 && (
        <div className="mb-3 flex flex-wrap items-baseline gap-x-6 gap-y-1 text-sm">
          {data.changePct != null && data.change != null && (
            <span className={`font-semibold ${getReturnColor(data.changePct)}`}>
              {signed(data.change, formatValue(data, data.change))}
              {` (${signed(data.changePct, num(data.changePct, 2))}%)`}
            </span>
          )}
          {data.accumulatedPct != null && (
            <span className="text-gray-400">acumulado{' '}
              <span className={`font-semibold ${getReturnColor(data.accumulatedPct)}`}>
                {signed(data.accumulatedPct, num(data.accumulatedPct, 2))}%
              </span>
            </span>
          )}
          {data.last != null && data.unit !== 'PONTOS' && data.unit !== 'BRL' && (
            <span className="text-gray-400">{monthly ? (view === 'ANUAL' ? 'últimos 12 meses' : 'último') : 'atual'}{' '}
              <span className="text-white">{formatValue(data, data.last)}</span>
            </span>
          )}
          <span className="text-gray-400">mín <span className="text-white">{data.min != null ? formatValue(data, data.min) : '—'}</span></span>
          <span className="text-gray-400">máx <span className="text-white">{data.max != null ? formatValue(data, data.max) : '—'}</span></span>
          <span className="text-xs text-gray-500">
            {data.from && data.to && (monthly
              ? `${tooltipDate(data.from, true)} a ${tooltipDate(data.to, true)}`
              : `${formatDate(data.from)} a ${formatDate(data.to)}`)}
          </span>
        </div>
      )}

      <div className="h-72">
        {isLoading && !data ? (
          <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-6 w-6 text-accent" /></div>
        ) : isError ? (
          <div className="flex h-full items-center justify-center text-sm text-gray-400">
            {apiMessage(error) ?? 'Não foi possível carregar o índice.'}
          </div>
        ) : points.length === 0 ? (
          <div className="flex h-full items-center justify-center text-sm text-gray-400">
            {data?.lastDate ? `Sem valor de ${code} no período escolhido.` : `Série de ${code} ainda não carregada.`}
          </div>
        ) : bars ? (
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <CartesianGrid stroke={grid} strokeDasharray="3 3" vertical={false} />
              {xAxis}
              {yAxis}
              <ReferenceLine y={0} stroke={axis} />
              <Tooltip content={<ChartTooltip />} cursor={{ fill: isDark ? '#ffffff0d' : '#0000000a' }} />
              <Bar dataKey="value" fill={GOLD} radius={[4, 4, 0, 0]} maxBarSize={28} isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <defs>
                <linearGradient id={fillId} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={GOLD} stopOpacity={0.25} />
                  <stop offset="100%" stopColor={GOLD} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke={grid} strokeDasharray="3 3" vertical={false} />
              {xAxis}
              {yAxis}
              <Tooltip content={<ChartTooltip />} cursor={{ stroke: axis, strokeDasharray: '3 3' }} />
              <Area
                type={code === 'SELIC_META' ? 'stepAfter' : 'linear'}
                dataKey="value"
                stroke={GOLD}
                strokeWidth={2}
                fill={`url(#${fillId})`}
                dot={points.length <= 10 ? { r: 4, fill: GOLD, strokeWidth: 0 } : false}
                activeDot={{ r: 4, fill: GOLD, stroke: isDark ? '#111111' : '#ffffff', strokeWidth: 2 }}
                isAnimationActive={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>
    </Card>
  );
};
