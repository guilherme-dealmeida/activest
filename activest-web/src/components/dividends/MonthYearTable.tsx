import { clsx } from 'clsx';
import { formatCurrency } from '@/utils/format';
import { DividendPeriods, MONTH_SHORT, monthLabel } from '@/utils/dividendPeriods';
import { heatBackground } from './evolutionColors';

const money = (cents: number) => formatCurrency(cents / 100);
/** Célula da tabela: sem o "R$" (a legenda diz que é em reais), para os 12 meses caberem na largura. */
const amount = (cents: number) =>
  (cents / 100).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const monthKey = (year: number, i: number) => `${year}-${String(i + 1).padStart(2, '0')}`;

/**
 * Bloco E da Evolução dos Proventos (docs/planos/PLANO_DIVIDENDOS_POR_PERIODO.md): ano × mês, total e média mensal do
 * ano, linha Total. A intensidade do dourado acompanha o valor do mês (o maior mês = a cor mais forte); mês com a
 * receber fica em itálico. Clique no mês abre o detalhe (D6).
 */
export const MonthYearTable = ({ periods, selected, onSelect }: {
  periods: Pick<DividendPeriods, 'months' | 'years'>; selected: string | null; onSelect: (key: string) => void;
}) => {
  const byKey = new Map(periods.months.map(m => [m.key, m]));
  const max = Math.max(1, ...periods.months.map(m => m.totalCents));
  const years = [...periods.years].sort((a, b) => b.year - a.year);
  const columnTotals = MONTH_SHORT.map((_, i) =>
    years.reduce((s, y) => s + (byKey.get(monthKey(y.year, i))?.totalCents ?? 0), 0));
  const grand = years.reduce((s, y) => s + y.totalCents, 0);

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[880px] text-xs border-separate tabular-nums" style={{ borderSpacing: 2 }}>
        <thead>
          <tr className="text-gray-400">
            <th className="px-2 py-1 text-left font-medium">Ano</th>
            {MONTH_SHORT.map(m => <th key={m} className="px-1 py-1 text-right font-medium capitalize">{m}</th>)}
            <th className="px-2 py-1 text-right font-medium">Total</th>
            <th className="px-2 py-1 text-right font-medium">Média mensal</th>
          </tr>
        </thead>
        <tbody>
          {years.map(y => (
            <tr key={y.year}>
              <td className="px-2 py-1.5 font-medium text-gray-300 whitespace-nowrap">
                {y.year}{y.partial && <span className="ml-1 text-[10px] font-normal text-gray-500">parcial</span>}
              </td>
              {MONTH_SHORT.map((_, i) => {
                const key = monthKey(y.year, i);
                const m = byKey.get(key);
                if (!m) return <td key={key} />;
                const pending = m.toReceiveCents > 0;
                const title = [
                  `${monthLabel(key)}: ${money(m.totalCents)}`,
                  pending ? `recebido ${money(m.receivedCents)} · a receber ${money(m.toReceiveCents)}` : '',
                  m.current ? 'mês em andamento' : '',
                ].filter(Boolean).join(' · ');
                return (
                  <td key={key} className="p-0">
                    <button
                      type="button"
                      onClick={() => onSelect(key)}
                      title={title}
                      aria-pressed={selected === key}
                      className={clsx(
                        'w-full rounded px-1.5 py-1.5 text-right text-gray-200 transition-shadow hover:ring-1 hover:ring-accent/60',
                        pending && 'italic',
                        m.totalCents === 0 && 'text-gray-500',
                        selected === key && 'ring-2 ring-accent',
                      )}
                      style={{ background: m.totalCents > 0 ? heatBackground(m.totalCents / max) : undefined }}
                    >
                      {m.totalCents > 0 ? amount(m.totalCents) : '—'}
                    </button>
                  </td>
                );
              })}
              <td className="px-2 py-1.5 text-right font-semibold text-white whitespace-nowrap">{amount(y.totalCents)}</td>
              <td className="px-2 py-1.5 text-right text-gray-300 whitespace-nowrap"
                title={`${money(y.totalCents)} ÷ ${y.months} ${y.months === 1 ? 'mês' : 'meses'}`}>
                {amount(y.monthlyAverageCents)}
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr className="text-gray-300">
            <td className="px-2 py-1.5 font-medium border-t border-white/10">Total</td>
            {columnTotals.map((c, i) => (
              <td key={i} className="px-1.5 py-1.5 text-right border-t border-white/10 whitespace-nowrap">{amount(c)}</td>
            ))}
            <td className="px-2 py-1.5 text-right font-semibold text-white border-t border-white/10 whitespace-nowrap">{amount(grand)}</td>
            <td className="border-t border-white/10" />
          </tr>
        </tfoot>
      </table>
      <p className="mt-2 text-xs text-gray-500">
        Em R$, líquido, pela data de pagamento. Em itálico: o mês tem proventos a receber. A média mensal divide o total do ano
        pelos meses dele desde o primeiro provento até o último anunciado. Clique num mês para ver os proventos.
      </p>
    </div>
  );
};
