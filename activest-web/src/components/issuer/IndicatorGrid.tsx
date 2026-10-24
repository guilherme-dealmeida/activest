import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { LineChart } from 'lucide-react';
import { indicatorsApi } from '@/api/indicators';
import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { Toggle } from '@/components/ui/Toggle';
import { AssetIndicators, IndicatorCode, IndicatorHistory, IndicatorItem, IndicatorWindow } from '@/types';
import {
  DEFAULT_WINDOW, HISTORY_AVAILABLE, INDICATOR_GROUPS, INDICATOR_META, INDICATOR_WINDOWS, averageOf, formatIndicator,
  pointsInWindow,
} from '@/utils/indicators';
import { Sparkline } from './Sparkline';
import { IndicatorHistoryModal } from './IndicatorHistoryModal';

const WINDOW_OPTIONS = INDICATOR_WINDOWS.map(w => ({
  value: w, label: w, title: w === 'MAX' ? 'Toda a série' : `Últimos ${w === '3A' ? 3 : 5} exercícios + Atual`,
}));

/** Um cartão: valor atual, seletor 3A | 5A | MAX (D15, sem lembrar a escolha), sparkline e média da janela. */
const IndicatorCard = ({ code, item, history, loadingSpark, onOpen }: {
  code: IndicatorCode;
  item: IndicatorItem;
  history: IndicatorHistory | undefined;
  loadingSpark: boolean;
  onOpen: (code: IndicatorCode, window: IndicatorWindow) => void;
}) => {
  const [win, setWin] = useState<IndicatorWindow>(DEFAULT_WINDOW);
  const meta = INDICATOR_META[code];
  const hasHistory = HISTORY_AVAILABLE.has(code);
  const avg = history ? averageOf(history, win) : null;
  return (
    <div className="flex flex-col rounded-xl border border-surface-light bg-surface p-4">
      <div className="flex items-start justify-between gap-2">
        <span className="flex items-center gap-1 text-sm font-medium text-gray-400">
          {meta.label}
          <InfoTooltip label={`O que é ${meta.label}`} content={<><strong>{meta.name}.</strong> {meta.formula}</>} />
        </span>
        {hasHistory && (
          <button
            type="button"
            onClick={() => onOpen(code, win)}
            aria-label={`Histórico de ${meta.label}`}
            title="Ver histórico"
            className="shrink-0 rounded-md p-1 text-gray-400 hover:bg-white/5 hover:text-accent transition-colors"
          >
            <LineChart className="h-4 w-4" />
          </button>
        )}
      </div>
      <div className="mt-1 flex flex-wrap items-center justify-between gap-x-2 gap-y-1">
        <div className="flex min-w-0 items-center gap-1.5">
          <span className="text-2xl font-semibold text-white">{formatIndicator(item.value, item.unit)}</span>
          {item.note && <InfoTooltip label="Motivo" content={item.note} />}
        </div>
        {hasHistory && (
          <Toggle label={`Período de ${meta.label}`} size="sm" value={win} onChange={setWin} options={WINDOW_OPTIONS} />
        )}
      </div>
      {hasHistory && (
        <button
          type="button"
          onClick={() => onOpen(code, win)}
          aria-label={`Abrir histórico de ${meta.label}`}
          className="mt-3 block w-full text-left"
        >
          {history ? (
            <Sparkline points={[...pointsInWindow(history, win), history.current]}
                       label={`Tendência anual de ${meta.label} (${win})`} />
          ) : loadingSpark ? (
            <div className="h-9 animate-pulse rounded bg-white/5" aria-hidden="true" />
          ) : null}
          {avg && avg.value != null && (
            <span className="mt-1 block text-[11px] text-gray-500">
              média {win} {formatIndicator(avg.value, history!.unit)}
              {avg.fromYear != null && ` · desde ${avg.fromYear}`}
            </span>
          )}
        </button>
      )}
    </div>
  );
};

/**
 * Cartões de indicadores com sparkline e botão de histórico (docs/planos/PLANO_TELA_EMISSORES.md, 2.4;
 * docs/planos/PLANO_TELA_ATIVOS.md, D14–D17): ações em grupos com subtítulo, FII num grupo só. Os cartões aparecem com
 * os valores atuais; as sparklines chegam depois, numa chamada só (B4). Banco e seguradora: os indicadores só do
 * comercial não vêm do backend, e uma linha explica (D17).
 */
export const IndicatorGrid = ({ current }: { current: AssetIndicators }) => {
  const ticker = current.ticker;
  const [open, setOpen] = useState<{ code: IndicatorCode; window: IndicatorWindow } | null>(null);

  const { data: annual, isLoading: loadingSpark } = useQuery({
    queryKey: ['indicator-history-all', ticker, 'YEAR'],
    queryFn: () => indicatorsApi.historyAll(ticker, 'YEAR'),
    staleTime: 5 * 60_000,
  });

  const byCode = new Map(current.indicators.map(i => [i.indicator, i]));
  const groups = INDICATOR_GROUPS[current.kind]
    .map(g => ({ ...g, codes: g.codes.filter(c => byCode.has(c)) }))
    .filter(g => g.codes.length > 0);
  const historyCodes = groups.flatMap(g => g.codes).filter(c => HISTORY_AVAILABLE.has(c));
  const series = (code: IndicatorCode): IndicatorHistory | undefined => annual?.find(h => h.indicator === code);
  const financial = current.statementLayout === 'BANCO' || current.statementLayout === 'SEGURADORA';

  return (
    <section aria-labelledby="indicadores-titulo" className="space-y-4">
      <h3 id="indicadores-titulo" className="text-sm font-medium text-gray-400">Indicadores</h3>
      {financial && (
        <p className="text-xs text-gray-500">
          Indicadores de margem, EBIT/EBITDA, dívida e capital de giro não se aplicam a bancos e seguradoras.
        </p>
      )}
      {groups.map(g => (
        <div key={g.title ?? 'all'}>
          {g.title && <h4 className="mb-2 text-xs font-medium uppercase tracking-wide text-gray-500">{g.title}</h4>}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {g.codes.map(code => (
              <IndicatorCard key={code} code={code} item={byCode.get(code)!} history={series(code)}
                             loadingSpark={loadingSpark} onOpen={(c, w) => setOpen({ code: c, window: w })} />
            ))}
          </div>
        </div>
      ))}

      <IndicatorHistoryModal ticker={ticker} codes={historyCodes} open={open?.code ?? null}
                             initialWindow={open?.window ?? DEFAULT_WINDOW}
                             onChange={code => setOpen(code ? { code, window: open?.window ?? DEFAULT_WINDOW } : null)}
                             annual={annual} />
    </section>
  );
};
