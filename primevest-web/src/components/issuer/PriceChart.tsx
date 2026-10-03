import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { assetsApi } from '@/api/assets';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { useTheme } from '@/context/ThemeContext';
import { PriceRange } from '@/types';
import { formatCurrency, formatDate, getReturnColor } from '@/utils/format';
import { apiMessage } from '@/utils/apiError';

const RANGES: PriceRange[] = ['1D', '7D', '1M', '6M', 'YTD', '1A', '5A', 'MAX'];
const DEFAULT_RANGE: PriceRange = '1A';
const GOLD = '#c9a84c';
const ISO = /^\d{4}-\d{2}-\d{2}$/;

const shortDate = (iso: string, long: boolean) => {
  const [y, m, d] = iso.slice(0, 10).split('-');
  return long ? `${m}/${y}` : `${d}/${m}`;
};

interface TooltipPayload {
  active?: boolean;
  payload?: { payload: { time: string; close: number } }[];
}

const PriceTooltip = ({ active, payload }: TooltipPayload) => {
  if (!active || !payload?.length) return null;
  const p = payload[0].payload;
  return (
    <div className="rounded-md border border-white/10 bg-surface px-3 py-2 text-xs shadow-lg">
      <div className="text-gray-400">{formatDate(p.time.slice(0, 10))}</div>
      <div className="mt-0.5 text-sm font-semibold text-white">{formatCurrency(p.close)}</div>
    </div>
  );
};

/**
 * Gráfico de cotação (docs/planos/PLANO_TELA_EMISSORES.md, 2.3, D7, D9): atalhos 1D…MAX
 * (1A padrão), período específico de/até, período guardado na URL. Preço
 * ajustado por desdobramento, grupamento e bonificação; densidade decidida
 * pelo servidor.
 */
export const PriceChart = ({ ticker }: { ticker: string }) => {
  const { isDark } = useTheme();
  const [params, setParams] = useSearchParams();
  const de = params.get('de');
  const ate = params.get('ate');
  const custom = (de && ISO.test(de)) || (ate && ISO.test(ate));
  const periodoParam = (params.get('periodo') ?? '').toUpperCase() as PriceRange;
  const range: PriceRange = RANGES.includes(periodoParam) ? periodoParam : DEFAULT_RANGE;

  const period = custom
    ? { from: de && ISO.test(de) ? de : undefined, to: ate && ISO.test(ate) ? ate : undefined }
    : { range };

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['prices', ticker, custom ? `${de ?? ''}_${ate ?? ''}` : range],
    queryFn: () => assetsApi.prices(ticker, period),
    placeholderData: (prev) => (prev?.ticker === ticker ? prev : undefined),
  });

  const setRange = (r: PriceRange) => {
    const next = new URLSearchParams(params);
    next.delete('de'); next.delete('ate');
    if (r === DEFAULT_RANGE) next.delete('periodo'); else next.set('periodo', r);
    setParams(next, { replace: true });
  };

  const setDate = (key: 'de' | 'ate', value: string) => {
    const next = new URLSearchParams(params);
    next.delete('periodo');
    if (value) next.set(key, value); else next.delete(key);
    // o outro extremo começa no intervalo mostrado agora, para o período não "pular"
    const other = key === 'de' ? 'ate' : 'de';
    if (!next.get(other) && data) {
      const v = other === 'de' ? data.from : data.to;
      if (v) next.set(other, v);
    }
    if (!next.get('de') && !next.get('ate')) next.delete('periodo');
    setParams(next, { replace: true });
  };

  const longAxis = useMemo(() => {
    if (!data?.from || !data?.to) return false;
    return (new Date(data.to).getTime() - new Date(data.from).getTime()) / 86_400_000 > 400;
  }, [data]);

  const grid = isDark ? '#2a2a2a' : '#e8e8e0';
  const axis = isDark ? '#777777' : '#777777';
  const points = data?.points ?? [];

  return (
    <Card>
      <div className="flex flex-col gap-3 mb-4 lg:flex-row lg:items-center lg:justify-between">
        <h3 className="text-sm font-medium text-gray-400 flex items-center gap-1.5">
          Cotação
          <InfoTooltip content="Fechamento diário da B3 desde 2010. Preços anteriores a desdobramentos, grupamentos e bonificações são ajustados para a quantidade de hoje; proventos não entram no ajuste." />
        </h3>
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex flex-wrap gap-1" role="group" aria-label="Período">
            {RANGES.map(r => (
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
            <label htmlFor="cot-de">de</label>
            <input
              id="cot-de" type="date"
              value={custom ? (de ?? data?.from ?? '') : ''}
              min={data?.firstDate ?? undefined} max={data?.lastDate ?? undefined}
              onChange={(e) => setDate('de', e.target.value)}
              className="rounded-md border border-surface-light bg-surface px-2 py-1 text-xs text-white"
            />
            <label htmlFor="cot-ate">até</label>
            <input
              id="cot-ate" type="date"
              value={custom ? (ate ?? data?.to ?? '') : ''}
              min={data?.firstDate ?? undefined} max={data?.lastDate ?? undefined}
              onChange={(e) => setDate('ate', e.target.value)}
              className="rounded-md border border-surface-light bg-surface px-2 py-1 text-xs text-white"
            />
          </div>
        </div>
      </div>

      {data && points.length > 0 && (
        <div className="mb-3 flex flex-wrap items-baseline gap-x-6 gap-y-1 text-sm">
          <span className={`font-semibold ${getReturnColor(data.changePct ?? 0)}`}>
            {data.change != null && (data.change > 0 ? '+' : '')}
            {data.change != null ? formatCurrency(data.change) : '—'}
            {data.changePct != null && ` (${data.changePct > 0 ? '+' : ''}${data.changePct.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%)`}
          </span>
          <span className="text-gray-400">mín <span className="text-white">{data.min != null ? formatCurrency(data.min) : '—'}</span></span>
          <span className="text-gray-400">máx <span className="text-white">{data.max != null ? formatCurrency(data.max) : '—'}</span></span>
          <span className="text-xs text-gray-500">
            {data.from && data.to && `${formatDate(data.from)} a ${formatDate(data.to)}`}
            {!custom && range === '1D' && data.closeOnly && ' · só fechamento'}
            {data.adjusted && ' · ajustado por eventos societários'}
            {data.priceTicker !== data.ticker && ` · preço do ${data.priceTicker}`}
          </span>
        </div>
      )}

      <div className="h-72">
        {isLoading && !data ? (
          <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-6 w-6 text-accent" /></div>
        ) : isError ? (
          <div className="flex h-full items-center justify-center text-sm text-gray-400">
            {apiMessage(error) ?? 'Não foi possível carregar a cotação.'}
          </div>
        ) : points.length === 0 ? (
          <div className="flex h-full items-center justify-center text-sm text-gray-400">
            Sem cotação registrada para {ticker}{custom ? ' no período escolhido' : ''}.
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <defs>
                <linearGradient id="cotacaoFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={GOLD} stopOpacity={0.25} />
                  <stop offset="100%" stopColor={GOLD} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke={grid} strokeDasharray="3 3" vertical={false} />
              <XAxis
                dataKey="time"
                tickFormatter={(v: string) => shortDate(v, longAxis)}
                tick={{ fill: axis, fontSize: 11 }}
                tickLine={false}
                axisLine={{ stroke: grid }}
                minTickGap={32}
              />
              <YAxis
                domain={['auto', 'auto']}
                tickFormatter={(v: number) => v.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}
                tick={{ fill: axis, fontSize: 11 }}
                tickLine={false}
                axisLine={false}
                width={56}
              />
              <Tooltip content={<PriceTooltip />} cursor={{ stroke: axis, strokeDasharray: '3 3' }} />
              <Area
                type="linear"
                dataKey="close"
                stroke={GOLD}
                strokeWidth={2}
                fill="url(#cotacaoFill)"
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
