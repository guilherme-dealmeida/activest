import { useEffect, useMemo, useState } from 'react';
import { Copy } from 'lucide-react';
import { clsx } from 'clsx';
import { Card } from '@/components/ui/Card';
import { Switch } from '@/components/ui/Switch';
import { useToast } from '@/context/ToastContext';
import { DividendResponse } from '@/types';
import { copyToClipboard } from '@/utils/clipboard';
import { formatCurrency, toLocalISODate } from '@/utils/format';
import {
  MonthWindow, buildDividendPeriods, monthsInWindow, periodsToTsv, windowAverageCents,
} from '@/utils/dividendPeriods';
import { DividendFilterBar, DividendFilterNotice } from './DividendFilters';
import { useDividendFilters } from './useDividendFilters';
import { EvolutionLegend, MonthlyChart, YearlyChart } from './EvolutionCharts';
import { MonthYearTable } from './MonthYearTable';
import { MonthDetailPanel } from './MonthDetailPanel';
import { useColoredSeries } from './evolutionColors';

/** Gráfico mostrado: por mês ou por ano — um de cada vez (R3, 27/10/2026). */
type ChartView = 'month' | 'year';

const WINDOWS: { value: MonthWindow; label: string }[] = [
  { value: '12M', label: '12M' },
  { value: '24M', label: '24M' },
  { value: 'MAX', label: 'MAX' },
];

const pill = (active: boolean) =>
  `rounded-md px-2.5 py-1 text-xs font-medium ${active ? 'bg-accent text-black' : 'bg-white/5 text-gray-300 hover:bg-white/10'}`;

/**
 * Aba "Evolução dos Proventos" (27/10/2026, docs/planos/PLANO_DIVIDENDOS_POR_PERIODO.md): valores mensais e anuais dos
 * proventos recebidos e a receber, sempre líquidos e pela data de pagamento (D3, D4), sem cards. Um gráfico de cada vez,
 * por mês ou por ano (R3), com o recebido na cor do tipo de ativo (FIIs na base) e o a receber hachurado no topo (D5,
 * R2); tabela mês a mês, com ou sem o a receber (R5), detalhe do mês (D6) e "Copiar tabela" (D7). Tudo no navegador,
 * sobre os proventos que a tela já carrega (D8).
 */
export const DividendEvolutionTab = ({ rows, onShowInList }: {
  rows: DividendResponse[]; onShowInList: (ticker: string, received: boolean) => void;
}) => {
  const { showToast } = useToast();
  const f = useDividendFilters(rows);
  const today = toLocalISODate();
  const periods = useMemo(() => buildDividendPeriods(f.filtered, today), [f.filtered, today]);
  const types = useColoredSeries(periods.types);
  const [view, setView] = useState<ChartView>('month');
  const [range, setRange] = useState<MonthWindow>('12M');
  const [selected, setSelected] = useState<string | null>(null);
  const [includeToReceive, setIncludeToReceive] = useState(true);

  // Tabela, cópia e detalhe do mês: com ou sem o a receber (R5).
  const tableRows = useMemo(
    () => (includeToReceive ? f.filtered : f.filtered.filter(r => r.received)),
    [f.filtered, includeToReceive]
  );
  const tablePeriods = useMemo(
    () => (includeToReceive ? periods : buildDividendPeriods(tableRows, today)),
    [includeToReceive, periods, tableRows, today]
  );

  const windowMonths = useMemo(
    () => monthsInWindow(periods.months, range, periods.currentMonth),
    [periods, range]
  );
  const average = useMemo(() => windowAverageCents(windowMonths, periods.currentMonth), [windowMonths, periods]);
  const years = useMemo(() => periods.years.map(y => y.year).sort((a, b) => b - a), [periods]);
  const hasToReceive = periods.months.some(m => m.toReceiveCents > 0);

  // Ano escolhido que sumiu com o filtro: volta para 12M.
  useEffect(() => {
    if (typeof range === 'number' && !years.includes(range)) setRange('12M');
  }, [range, years]);

  const select = (key: string) => setSelected(key);
  /** Clique no gráfico: o detalhe fica abaixo da tabela, então a tela desce até ele. */
  const selectFromChart = (key: string) => {
    setSelected(key);
    requestAnimationFrame(() =>
      document.getElementById('dividend-month-detail')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  };

  const copyTable = async () => {
    const ok = await copyToClipboard(periodsToTsv(tablePeriods));
    showToast(ok ? 'Tabela copiada. Cole no Excel ou no Planilhas.' : 'Não foi possível copiar a tabela.', ok ? 'success' : 'error');
  };

  const empty = periods.months.length === 0;

  return (
    <div className="space-y-6">
      <DividendFilterNotice f={f} />
      <DividendFilterBar f={f} />

      {empty ? (
        <Card><p className="text-sm text-gray-500">Nenhum provento no filtro.</p></Card>
      ) : (
        <>
          <Card>
            <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
              <div>
                <h3 className="text-lg font-medium text-white">{view === 'month' ? 'Proventos por mês' : 'Proventos por ano'}</h3>
                <p className="text-xs text-gray-500">
                  {view === 'month'
                    ? 'Líquido, pela data de pagamento. Clique num mês para ver os proventos.'
                    : 'Líquido, pela data de pagamento. O crescimento sobre o ano anterior aparece no balão, entre anos completos.'}
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                {/* Mês | Ano: um gráfico de cada vez, no estilo das abas da tela */}
                <div className="flex space-x-1 rounded-lg border border-surface-light bg-surface p-1" role="group" aria-label="Gráfico">
                  {(['month', 'year'] as ChartView[]).map(v => (
                    <button key={v} type="button" onClick={() => setView(v)} aria-pressed={view === v}
                      className={clsx('rounded-md px-3 py-1 text-xs font-medium transition-colors',
                        view === v ? 'bg-surface-light text-white' : 'text-gray-400 hover:bg-surface-light/50 hover:text-white')}>
                      {v === 'month' ? 'Por mês' : 'Por ano'}
                    </button>
                  ))}
                </div>
                {view === 'month' && (
                  <div className="flex items-center gap-1" role="group" aria-label="Período">
                    {WINDOWS.map(w => (
                      <button key={w.label} type="button" onClick={() => setRange(w.value)} aria-pressed={range === w.value}
                        className={pill(range === w.value)}>
                        {w.label}
                      </button>
                    ))}
                    <select
                      value={typeof range === 'number' ? String(range) : ''}
                      onChange={(e) => setRange(e.target.value ? Number(e.target.value) : '12M')}
                      aria-label="Ano"
                      className={clsx(
                        'rounded-md border-0 py-1 pl-2.5 pr-7 text-xs font-medium focus:ring-accent',
                        typeof range === 'number' ? 'bg-accent text-black' : 'bg-surface-light text-gray-300'
                      )}
                    >
                      {/* opções com o fundo e o texto do tema: herdar do select deixava texto claro em fundo branco */}
                      <option value="" className="bg-surface text-white">Ano</option>
                      {years.map(y => <option key={y} value={y} className="bg-surface text-white">{y}</option>)}
                    </select>
                  </div>
                )}
              </div>
            </div>
            <div className="mb-3"><EvolutionLegend types={types} hasToReceive={hasToReceive} /></div>
            {view === 'month'
              ? <MonthlyChart months={windowMonths} types={types} averageCents={average} onSelect={selectFromChart} />
              : <YearlyChart years={periods.years} types={types} />}
          </Card>

          <Card>
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <h3 className="text-lg font-medium text-white">Mês a mês</h3>
              <div className="flex flex-wrap items-center gap-4">
                <label className="inline-flex items-center gap-2 text-xs text-gray-300">
                  <Switch checked={includeToReceive} onChange={setIncludeToReceive} label="Incluir proventos a receber" />
                  Incluir a receber
                </label>
                <button type="button" onClick={copyTable}
                  className="inline-flex items-center gap-1.5 rounded-md bg-white/5 px-3 py-1.5 text-xs font-medium text-gray-300 hover:bg-white/10 hover:text-white">
                  <Copy className="h-3.5 w-3.5" />
                  Copiar tabela
                </button>
              </div>
            </div>
            <MonthYearTable periods={tablePeriods} selected={selected} onSelect={select} />
          </Card>

          {/* sempre na tela (R6): sem mês escolhido, o card "Proventos" com a instrução */}
          <MonthDetailPanel monthKey={selected} rows={tableRows} onClose={() => setSelected(null)}
            onShowInList={onShowInList} />
        </>
      )}

      {periods.undated.count > 0 && (
        <p className="text-xs text-gray-500">
          {periods.undated.count} {periods.undated.count === 1 ? 'provento a receber' : 'proventos a receber'} sem data de
          pagamento ({formatCurrency(periods.undated.cents / 100)}) {periods.undated.count === 1 ? 'fica' : 'ficam'} fora
          dos meses até a empresa divulgar a data.
        </p>
      )}
    </div>
  );
};
