import { useMemo } from 'react';
import { List, X } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { TABLE_ACTION_CLASS } from '@/components/ui/tableAction';
import { useSortable } from '@/hooks/useSortable';
import { formatCurrency } from '@/utils/format';
import { DividendResponse } from '@/types';
import { MonthDetailRow, monthDetail, monthLabel } from '@/utils/dividendPeriods';
import { TYPE_GROUP_LABELS } from './dividendLabels';

const money = (cents: number) => formatCurrency(cents / 100);

/** Linha da tabela: o agrupado do mês com os textos que a ordenação usa. */
type DetailRow = MonthDetailRow & { typesLabel: string };

/** Colunas ordenáveis: chave do `DetailRow` e rótulo; as numéricas alinham à direita. */
const COLUMNS: { key: keyof DetailRow; label: string; numeric?: boolean }[] = [
  { key: 'ticker', label: 'Ativo' },
  { key: 'assetTypeDescription', label: 'Tipo Ativo' },
  { key: 'typesLabel', label: 'Tipo' },
  { key: 'count', label: 'Proventos', numeric: true },
  { key: 'grossCents', label: 'Bruto', numeric: true },
  { key: 'taxCents', label: 'IR', numeric: true },
  { key: 'netCents', label: 'Líquido', numeric: true },
];

/**
 * Detalhe do mês (D6): os proventos do mês agrupados por ativo, com bruto, IR e líquido — para achar o provento que
 * explica a diferença com outra plataforma. Fica sempre na tela (R6, 27/10/2026): sem mês escolhido, o card "Proventos"
 * com a instrução. Colunas ordenáveis (padrão: líquido decrescente). O Listar abre o Histórico (ou o A Receber)
 * filtrado pelo ativo.
 */
export const MonthDetailPanel = ({ monthKey, rows, onClose, onShowInList }: {
  monthKey: string | null; rows: DividendResponse[]; onClose: () => void;
  onShowInList: (ticker: string, received: boolean) => void;
}) => {
  const detail = useMemo<DetailRow[]>(
    () => (monthKey ? monthDetail(rows, monthKey) : []).map(r => ({
      ...r, typesLabel: r.types.map(t => TYPE_GROUP_LABELS[t] ?? t).join(', '),
    })),
    [rows, monthKey]
  );
  const { sortedData, requestSort, getSortIndicator, getSortClass } = useSortable(detail, { key: 'netCents', direction: 'desc' });
  const sum = (f: (r: DetailRow) => number) => detail.reduce((s, r) => s + f(r), 0);
  const count = sum(r => r.count);

  return (
    <Card id="dividend-month-detail" className="scroll-mt-4">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div>
          <h3 className="text-lg font-medium text-white">{monthKey ? `Proventos de ${monthLabel(monthKey)}` : 'Proventos'}</h3>
          {monthKey && (
            <p className="text-xs text-gray-500">
              {count} {count === 1 ? 'provento' : 'proventos'} de {detail.length} {detail.length === 1 ? 'ativo' : 'ativos'},
              pela data de pagamento
            </p>
          )}
        </div>
        {monthKey && (
          <button type="button" onClick={onClose} className="rounded p-1 text-gray-400 hover:bg-white/5 hover:text-white"
            aria-label="Limpar o mês escolhido" title="Limpar o mês escolhido">
            <X className="h-4 w-4" />
          </button>
        )}
      </div>
      {!monthKey ? (
        <p className="text-sm text-gray-500">Clique num mês do gráfico ou da tabela "Mês a mês" para ver os proventos dele por ativo.</p>
      ) : detail.length === 0 ? (
        <p className="text-sm text-gray-500">Nenhum provento neste mês.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left tabular-nums">
            <thead className="text-xs uppercase border-b border-white/10">
              <tr>
                {COLUMNS.map(c => (
                  <th key={c.key} onClick={() => requestSort(c.key)}
                    className={`px-4 py-2 cursor-pointer select-none whitespace-nowrap ${c.numeric ? 'text-right' : ''} ${getSortClass(c.key)}`}>
                    {c.label}{getSortIndicator(c.key)}
                  </th>
                ))}
                <th className="px-4 py-2 text-center text-gray-400">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {sortedData.map(r => (
                <tr key={r.ticker} className="hover:bg-white/5">
                  <td className="px-4 py-2 font-medium text-white" title={r.assetName || undefined}>{r.ticker}</td>
                  <td className="px-4 py-2 text-gray-300">{r.assetTypeDescription ?? '—'}</td>
                  <td className="px-4 py-2 text-gray-300">
                    {r.typesLabel}
                    {r.toReceiveCount > 0 && (
                      <span className="ml-2 text-xs italic text-gray-500">
                        {r.receivedCount > 0 ? `${r.toReceiveCount} a receber` : 'a receber'}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-2 text-right">{r.count}</td>
                  <td className="px-4 py-2 text-right">{money(r.grossCents)}</td>
                  <td className="px-4 py-2 text-right text-gray-400">{money(r.taxCents)}</td>
                  <td className="px-4 py-2 text-right font-medium text-success">{money(r.netCents)}</td>
                  <td className="px-4 py-2 text-center">
                    <button type="button" className={TABLE_ACTION_CLASS} disabled={r.assetTypeId == null}
                      onClick={() => onShowInList(r.ticker, r.receivedCount > 0)}
                      title={r.receivedCount > 0 ? 'Ver no Histórico de Proventos' : 'Ver em Proventos a Receber'}>
                      <List className="h-4 w-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="font-semibold text-white border-t border-white/10">
                <td className="px-4 py-2" colSpan={3}>Total</td>
                <td className="px-4 py-2 text-right">{count}</td>
                <td className="px-4 py-2 text-right">{money(sum(r => r.grossCents))}</td>
                <td className="px-4 py-2 text-right text-gray-400">{money(sum(r => r.taxCents))}</td>
                <td className="px-4 py-2 text-right text-success">{money(sum(r => r.netCents))}</td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </Card>
  );
};
