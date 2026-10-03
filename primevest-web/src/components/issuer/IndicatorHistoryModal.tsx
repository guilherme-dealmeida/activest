import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { indicatorsApi } from '@/api/indicators';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { useTheme } from '@/context/ThemeContext';
import { IndicatorCode, IndicatorHistory, IndicatorPeriod, IndicatorPoint, IndicatorUnit } from '@/types';
import { INDICATOR_META, formatIndicator, formatIndicatorAxis } from '@/utils/indicators';
import { apiMessage } from '@/utils/apiError';

interface Props {
  ticker: string;
  /** Indicadores navegáveis com ‹ › (a ordem dos cartões). */
  codes: IndicatorCode[];
  open: IndicatorCode | null;
  onChange: (code: IndicatorCode | null) => void;
  /** Históricos anuais já carregados para as sparklines (B4). */
  annual: IndicatorHistory[] | undefined;
}

const GOLD = '#c9a84c';

interface BarDatum extends IndicatorPoint {
  plot: number;
  isCurrent: boolean;
}

const BarTooltip = ({ active, payload, unit }: {
  active?: boolean; payload?: { payload: BarDatum }[]; unit: IndicatorUnit;
}) => {
  if (!active || !payload?.length) return null;
  const p = payload[0].payload;
  return (
    <div className="max-w-[240px] rounded-md border border-white/10 bg-surface px-3 py-2 text-xs shadow-lg">
      <div className="text-gray-400">{p.label}</div>
      <div className="mt-0.5 text-sm font-semibold text-white">{formatIndicator(p.value, unit, true)}</div>
      {p.note && <div className="mt-1 text-gray-400">{p.note}</div>}
    </div>
  );
};

/**
 * Histórico de um indicador em modal (PLANO_TELA_EMISSORES.md, 2.4, D1):
 * Anual | Trimestral, barras com o Atual em dourado e a média da empresa
 * tracejada, tabela embaixo, ‹ › (e ← →) para o indicador vizinho.
 */
export const IndicatorHistoryModal = ({ ticker, codes, open, onChange, annual }: Props) => {
  const { isDark } = useTheme();
  const [period, setPeriod] = useState<IndicatorPeriod>('YEAR');
  const idx = open ? codes.indexOf(open) : -1;

  const fromBatch = open && period === 'YEAR' ? annual?.find(h => h.indicator === open) : undefined;
  const { data: fetched, isLoading, isError, error } = useQuery({
    queryKey: ['indicator-history', ticker, open, period],
    queryFn: () => indicatorsApi.history(ticker, open as IndicatorCode, period),
    enabled: !!open && !fromBatch,
    staleTime: 5 * 60_000,
  });
  const data = fromBatch ?? fetched;

  const go = (delta: number) => {
    if (idx < 0) return;
    onChange(codes[(idx + delta + codes.length) % codes.length]);
  };

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      if (target && ['INPUT', 'SELECT', 'TEXTAREA'].includes(target.tagName)) return;
      if (e.key === 'ArrowLeft') go(-1);
      else if (e.key === 'ArrowRight') go(1);
      else if (e.key === 'Escape') onChange(null);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  if (!open) return null;
  const meta = INDICATOR_META[open];
  const unit = data?.unit ?? 'RATIO';

  const bars: BarDatum[] = data
    ? [...data.points, data.current].map((p, i, all) => ({
        ...p, plot: p.value ?? 0, isCurrent: i === all.length - 1,
      }))
    : [];
  const grid = isDark ? '#2a2a2a' : '#e8e8e0';
  const axis = '#777777';
  const muted = isDark ? '#6b6b6b' : '#b8b8ae';

  return (
    <Modal
      isOpen
      onClose={() => onChange(null)}
      title={`${meta.label} · ${ticker}`}
      titleInfo={<><strong>{meta.name}.</strong> {meta.formula}</>}
      size="xl"
    >
      <div className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-baseline gap-x-6 gap-y-1">
            <div>
              <span className="text-xs uppercase text-gray-500">Atual</span>
              <div className="text-xl font-semibold text-accent">
                {data ? formatIndicator(data.current.value, unit) : '…'}
              </div>
            </div>
            <div>
              <span className="text-xs uppercase text-gray-500">Média da empresa</span>
              <div className="text-xl font-semibold text-white">
                {data ? formatIndicator(data.companyAverage, unit) : '…'}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <div className="flex rounded-md bg-white/5 p-0.5" role="group" aria-label="Periodicidade">
              {(['YEAR', 'QUARTER'] as IndicatorPeriod[]).map(p => (
                <button key={p} type="button" onClick={() => setPeriod(p)} aria-pressed={period === p}
                        className={`rounded px-3 py-1 text-xs font-medium transition-colors ${
                          period === p ? 'bg-accent text-black' : 'text-gray-300 hover:text-white'
                        }`}>
                  {p === 'YEAR' ? 'Anual' : 'Trimestral'}
                </button>
              ))}
            </div>
            <button type="button" onClick={() => go(-1)} aria-label="Indicador anterior"
                    className="rounded-md p-1.5 text-gray-400 hover:bg-white/5 hover:text-white">
              <ChevronLeft className="h-5 w-5" />
            </button>
            <button type="button" onClick={() => go(1)} aria-label="Próximo indicador"
                    className="rounded-md p-1.5 text-gray-400 hover:bg-white/5 hover:text-white">
              <ChevronRight className="h-5 w-5" />
            </button>
          </div>
        </div>

        {isLoading && !data ? (
          <div className="flex h-72 items-center justify-center"><LoadingSpinner className="h-6 w-6 text-accent" /></div>
        ) : isError && !data ? (
          <div className="flex h-72 items-center justify-center text-sm text-gray-400">
            {apiMessage(error) ?? 'Não foi possível carregar o histórico.'}
          </div>
        ) : data && bars.length <= 1 ? (
          <div className="flex h-40 items-center justify-center text-sm text-gray-400">Sem histórico para este indicador.</div>
        ) : data ? (
          <>
            <div className="overflow-x-auto">
              <div className="h-72" style={{ minWidth: bars.length > 40 ? bars.length * 18 : undefined }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={bars} margin={{ top: 16, right: 8, bottom: 0, left: 0 }} barCategoryGap="20%">
                    <CartesianGrid stroke={grid} strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="label" tick={{ fill: axis, fontSize: 11 }} tickLine={false}
                           axisLine={{ stroke: grid }} interval="preserveStartEnd" minTickGap={8} />
                    <YAxis tickFormatter={(v: number) => formatIndicatorAxis(v, unit)} tick={{ fill: axis, fontSize: 11 }}
                           tickLine={false} axisLine={false} width={60} />
                    <Tooltip content={<BarTooltip unit={unit} />} cursor={{ fill: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)' }} />
                    <ReferenceLine y={0} stroke={axis} strokeOpacity={0.5} />
                    {data.companyAverage != null && (
                      <ReferenceLine y={data.companyAverage} stroke={GOLD} strokeDasharray="6 4" strokeOpacity={0.9}
                                     label={{ value: `média ${formatIndicator(data.companyAverage, unit)}`, position: 'insideTopRight',
                                              fill: axis, fontSize: 11 }} />
                    )}
                    <Bar dataKey="plot" radius={[3, 3, 0, 0]} isAnimationActive={false}>
                      {bars.map((b, i) => (
                        <Cell key={i} fill={b.isCurrent ? GOLD : muted} fillOpacity={b.value == null ? 0 : 1} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div className="overflow-x-auto rounded-md border border-white/10">
              <table className="w-full text-xs">
                <tbody>
                  <tr className="border-b border-white/10">
                    {bars.map((b, i) => (
                      <th key={i} className={`px-2 py-1.5 text-center font-medium whitespace-nowrap ${b.isCurrent ? 'text-accent' : 'text-gray-400'}`}>
                        {b.label}
                      </th>
                    ))}
                    <th className="px-2 py-1.5 text-center font-medium text-gray-400">Média</th>
                  </tr>
                  <tr>
                    {bars.map((b, i) => (
                      <td key={i} title={b.note ?? undefined}
                          className={`px-2 py-1.5 text-center whitespace-nowrap ${b.isCurrent ? 'font-semibold text-accent' : 'text-white'} ${b.note ? 'cursor-help underline decoration-dotted decoration-gray-500' : ''}`}>
                        {formatIndicator(b.value, unit, true)}
                      </td>
                    ))}
                    <td className="px-2 py-1.5 text-center font-semibold text-white whitespace-nowrap">
                      {formatIndicator(data.companyAverage, unit, true)}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p className="text-xs text-gray-500">
              {period === 'YEAR'
                ? 'Ponto anual: balanço do exercício (ou informe de dezembro) com o fechamento da data. Média: pontos anuais + Atual; valores nulos ficam fora.'
                : 'Ponto trimestral: cada balanço (ou informe de março, junho, setembro e dezembro). A média é sempre a dos pontos anuais + Atual.'}
              {' '}Passe o mouse num “—” para ver o motivo.
            </p>
          </>
        ) : null}
      </div>
    </Modal>
  );
};
