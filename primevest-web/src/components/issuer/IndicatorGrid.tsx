import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { LineChart } from 'lucide-react';
import { indicatorsApi } from '@/api/indicators';
import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { AssetIndicators, IndicatorCode, IndicatorHistory } from '@/types';
import { HISTORICO_DISPONIVEL, INDICATOR_META, INDICATOR_ORDER, formatIndicator } from '@/utils/indicators';
import { Sparkline } from './Sparkline';
import { IndicatorHistoryModal } from './IndicatorHistoryModal';

/**
 * Cartões de indicadores com sparkline e botão de histórico
 * (PLANO_TELA_EMISSORES.md, 2.4, D1–D3). Os cartões aparecem com os valores
 * atuais; as sparklines chegam depois, numa chamada só (B4).
 */
export const IndicatorGrid = ({ current }: { current: AssetIndicators }) => {
  const ticker = current.ticker;
  const [open, setOpen] = useState<IndicatorCode | null>(null);

  const { data: annual, isLoading: loadingSpark } = useQuery({
    queryKey: ['indicator-history-all', ticker, 'YEAR'],
    queryFn: () => indicatorsApi.historyAll(ticker, 'YEAR'),
    staleTime: 5 * 60_000,
  });

  const byCode = new Map(current.indicators.map(i => [i.indicator, i]));
  const codes = INDICATOR_ORDER[current.kind].filter(c => byCode.has(c));
  const historyCodes = codes.filter(c => HISTORICO_DISPONIVEL.has(c));
  const series = (code: IndicatorCode): IndicatorHistory | undefined => annual?.find(h => h.indicator === code);

  return (
    <section aria-labelledby="indicadores-titulo">
      <h3 id="indicadores-titulo" className="mb-3 text-sm font-medium text-gray-400">Indicadores</h3>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {codes.map(code => {
          const item = byCode.get(code)!;
          const meta = INDICATOR_META[code];
          const hasHistory = HISTORICO_DISPONIVEL.has(code);
          const h = series(code);
          return (
            <div key={code} className="flex flex-col rounded-xl border border-surface-light bg-surface p-4">
              <div className="flex items-start justify-between gap-2">
                <span className="flex items-center gap-1 text-sm font-medium text-gray-400">
                  {meta.label}
                  <InfoTooltip label={`O que é ${meta.label}`} content={<><strong>{meta.name}.</strong> {meta.formula}</>} />
                </span>
                {hasHistory && (
                  <button
                    type="button"
                    onClick={() => setOpen(code)}
                    aria-label={`Histórico de ${meta.label}`}
                    title="Ver histórico"
                    className="rounded-md p-1 text-gray-400 hover:bg-white/5 hover:text-accent transition-colors"
                  >
                    <LineChart className="h-4 w-4" />
                  </button>
                )}
              </div>
              <div className="mt-1 flex items-center gap-1.5">
                <span className="text-2xl font-semibold text-white">{formatIndicator(item.value, item.unit)}</span>
                {item.note && <InfoTooltip label="Motivo" content={item.note} />}
              </div>
              {hasHistory && (
                <button
                  type="button"
                  onClick={() => setOpen(code)}
                  aria-label={`Abrir histórico de ${meta.label}`}
                  className="mt-3 block w-full text-left"
                >
                  {h ? (
                    <Sparkline points={[...h.points, h.current]} label={`Tendência anual de ${meta.label}`} />
                  ) : loadingSpark ? (
                    <div className="h-9 animate-pulse rounded bg-white/5" aria-hidden="true" />
                  ) : null}
                  {h && h.companyAverage != null && (
                    <span className="mt-1 block text-[11px] text-gray-500">
                      média {formatIndicator(h.companyAverage, h.unit)}
                      {h.points.length > 0 && ` · desde ${h.points[0].label}`}
                    </span>
                  )}
                </button>
              )}
            </div>
          );
        })}
      </div>

      <IndicatorHistoryModal ticker={ticker} codes={historyCodes} open={open} onChange={setOpen} annual={annual} />
    </section>
  );
};
