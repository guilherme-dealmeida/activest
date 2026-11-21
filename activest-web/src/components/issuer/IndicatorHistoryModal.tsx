import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { indicatorsApi } from '@/api/indicators';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Toggle } from '@/components/ui/Toggle';
import { IndicatorCode, IndicatorHistory, IndicatorPeriod, IndicatorPoint, IndicatorUnit, IndicatorWindow } from '@/types';
import { INDICATOR_META, INDICATOR_WINDOWS, averageOf, formatIndicator, formatIndicatorAxis, pointsInWindow } from '@/utils/indicators';
import { apiMessage } from '@/utils/apiError';
import { rem } from '@/utils/fontScale';
import { chartTheme } from '@/theme/chart';

interface Props {
  ticker: string;
  /** Indicadores navegáveis com ‹ › (a ordem dos cartões). */
  codes: IndicatorCode[];
  open: IndicatorCode | null;
  /** Janela do cartão que abriu o modal (D15); a do modal fica ao navegar com ‹ ›. */
  initialWindow: IndicatorWindow;
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
    <div className="max-w-[240px] rounded-control border border-line bg-surface px-3 py-2 text-xs shadow-lg">
      <div className="text-content-3">{p.label}</div>
      <div className="mt-0.5 text-sm font-semibold text-content">{formatIndicator(p.value, unit, true)}</div>
      {p.note && <div className="mt-1 text-content-3">{p.note}</div>}
    </div>
  );
};

/**
 * Histórico de um indicador em modal (docs/planos/PLANO_TELA_EMISSORES.md, 2.4, D1; docs/planos/PLANO_TELA_ATIVOS.md,
 * D14, D15): 3A | 5A | MAX à esquerda de Anual | Trimestral, barras com o Atual em dourado e a média da janela
 * tracejada, tabela embaixo, ‹ › (e ← →) para o indicador vizinho, mantendo a janela.
 */
export const IndicatorHistoryModal = ({ ticker, codes, open, initialWindow, onChange, annual }: Props) => {
  const [period, setPeriod] = useState<IndicatorPeriod>('YEAR');
  const [win, setWin] = useState<IndicatorWindow>(initialWindow);
  // ao abrir (fechado → aberto), começa na janela do cartão; navegando com ‹ › fica a do modal
  const wasOpen = useRef(false);
  useEffect(() => {
    if (open && !wasOpen.current) setWin(initialWindow);
    wasOpen.current = !!open;
  }, [open, initialWindow]);
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

  const average = data ? averageOf(data, win).value : null;
  const bars: BarDatum[] = data
    ? [...pointsInWindow(data, win), data.current].map((p, i, all) => ({
        ...p, plot: p.value ?? 0, isCurrent: i === all.length - 1,
      }))
    : [];
  const { grid, axis } = chartTheme();
  const muted = 'var(--content-4)';

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
              <span className="text-xs uppercase text-content-4">Atual</span>
              <div className="text-xl font-semibold text-accent-text">
                {data ? formatIndicator(data.current.value, unit) : '…'}
              </div>
            </div>
            <div>
              <span className="text-xs uppercase text-content-4">Média {win}</span>
              <div className="text-xl font-semibold text-content">
                {data ? formatIndicator(average, unit) : '…'}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Toggle label="Período" value={win} onChange={setWin}
                    options={INDICATOR_WINDOWS.map(w => ({ value: w, label: w }))} />
            <Toggle label="Periodicidade" value={period} onChange={setPeriod}
                    options={[{ value: 'YEAR', label: 'Anual' }, { value: 'QUARTER', label: 'Trimestral' }]} />
            <button type="button" onClick={() => go(-1)} aria-label="Indicador anterior"
                    className="rounded-control p-1.5 text-content-3 hover:bg-overlay hover:text-content">
              <ChevronLeft className="h-5 w-5" />
            </button>
            <button type="button" onClick={() => go(1)} aria-label="Próximo indicador"
                    className="rounded-control p-1.5 text-content-3 hover:bg-overlay hover:text-content">
              <ChevronRight className="h-5 w-5" />
            </button>
          </div>
        </div>

        {isLoading && !data ? (
          <div className="flex h-72 items-center justify-center"><LoadingSpinner className="h-6 w-6 text-accent-text" /></div>
        ) : isError && !data ? (
          <div className="flex h-72 items-center justify-center text-sm text-content-3">
            {apiMessage(error) ?? 'Não foi possível carregar o histórico.'}
          </div>
        ) : data && bars.length <= 1 ? (
          <div className="flex h-40 items-center justify-center text-sm text-content-3">Sem histórico para este indicador.</div>
        ) : data ? (
          <>
            <div className="overflow-x-auto">
              <div className="h-72" style={{ minWidth: bars.length > 40 ? bars.length * 18 : undefined }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={bars} margin={{ top: 16, right: 8, bottom: 0, left: 0 }} barCategoryGap="20%">
                    <CartesianGrid stroke={grid} strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="label" tick={{ fill: axis, fontSize: rem(11) }} tickLine={false}
                           axisLine={{ stroke: grid }} interval="preserveStartEnd" minTickGap={8} />
                    <YAxis tickFormatter={(v: number) => formatIndicatorAxis(v, unit)} tick={{ fill: axis, fontSize: rem(11) }}
                           tickLine={false} axisLine={false} width={60} />
                    <Tooltip content={<BarTooltip unit={unit} />} cursor={{ fill: 'var(--chart-cursor)' }} />
                    <ReferenceLine y={0} stroke={axis} strokeOpacity={0.5} />
                    {average != null && (
                      <ReferenceLine y={average} stroke={GOLD} strokeDasharray="6 4" strokeOpacity={0.9}
                                     label={{ value: `média ${win} ${formatIndicator(average, unit)}`, position: 'insideTopRight',
                                              fill: axis, fontSize: rem(11) }} />
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

            <div className="overflow-x-auto rounded-control border border-line">
              <table className="w-full text-xs">
                <tbody>
                  <tr className="border-b border-line">
                    {bars.map((b, i) => (
                      <th key={i} className={`px-2 py-1.5 text-center whitespace-nowrap ${b.isCurrent ? 'text-accent-text' : 'text-content-3'}`}>
                        {b.label}
                      </th>
                    ))}
                    <th className="px-2 py-1.5 text-center text-content-3">Média {win}</th>
                  </tr>
                  <tr>
                    {bars.map((b, i) => (
                      <td key={i} title={b.note ?? undefined}
                          className={`px-2 py-1.5 text-center whitespace-nowrap ${b.isCurrent ? 'font-semibold text-accent-text' : 'text-content'} ${b.note ? 'cursor-help underline decoration-dotted decoration-content-4' : ''}`}>
                        {formatIndicator(b.value, unit, true)}
                      </td>
                    ))}
                    <td className="px-2 py-1.5 text-center font-semibold text-content whitespace-nowrap">
                      {formatIndicator(average, unit, true)}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p className="text-xs text-content-4">
              {period === 'YEAR'
                ? 'Ponto anual: balanço do exercício (ou informe de dezembro) com o fechamento da data. Média: pontos anuais da janela + Atual; valores nulos ficam fora.'
                : 'Ponto trimestral: cada balanço (ou informe de março, junho, setembro e dezembro), desde o 1º trimestre do primeiro ano da janela. A média é sempre a dos pontos anuais da janela + Atual.'}
              {' '}Passe o mouse num “—” para ver o motivo.
            </p>
          </>
        ) : null}
      </div>
    </Modal>
  );
};
