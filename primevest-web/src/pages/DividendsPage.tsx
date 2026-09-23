import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { dividendsApi } from '@/api/dividends';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Pagination } from '@/components/ui/Pagination';
import { formatCurrency, formatDate } from '@/utils/format';
import { DividendType, DividendSummary } from '@/types';
import { DollarSign } from 'lucide-react';
import { useSortable } from '@/hooks/useSortable';

/**
 * Proventos são somente leitura: todos vêm da sincronização automática, que
 * calcula o total com as cotas que havia na data-com.
 */
export const DividendsPage = () => {
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);

  const { data: summary, isLoading } = useQuery<DividendSummary>({
    queryKey: ['dividends', 'summary'],
    queryFn: dividendsApi.getSummary
  });

  const recentData = summary?.recent || [];
  const { sortedData, requestSort, getSortIndicator, getSortClass } = useSortable(recentData);
  const totalPages = Math.ceil(sortedData.length / itemsPerPage);
  const paginated = sortedData.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <Card title="Total Recebido" value={formatCurrency(summary?.totalDividends || 0)} icon={<DollarSign className="text-success" />} className="md:col-span-1" />
        <Card className="md:col-span-3">
          <h3 className="text-sm font-medium text-gray-400 mb-4">Por Tipo</h3>
          <div className="flex gap-8">
            <div>
              <p className="text-sm text-gray-500">Dividendos</p>
              <p className="text-xl font-semibold text-white">{formatCurrency(summary?.byType['DIVIDENDO'] || 0)}</p>
            </div>
            <div>
              <p className="text-sm text-gray-500">JCP</p>
              <p className="text-xl font-semibold text-white">{formatCurrency(summary?.byType['JCP'] || 0)}</p>
            </div>
            <div>
              <p className="text-sm text-gray-500">Rendimentos</p>
              <p className="text-xl font-semibold text-white">{formatCurrency(summary?.byType['RENDIMENTO'] || 0)}</p>
            </div>
          </div>
        </Card>
      </div>

      <Card>
        <div className="mb-6">
          <h3 className="text-lg font-medium text-white">Histórico de Proventos</h3>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-gray-400 uppercase border-b border-white/10">
              <tr>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('exDate')}`} onClick={() => requestSort('exDate')}>
                  DATA BASE{getSortIndicator('exDate')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('paymentDate')}`} onClick={() => requestSort('paymentDate')}>
                  PAGAMENTO{getSortIndicator('paymentDate')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('ticker')}`} onClick={() => requestSort('ticker')}>
                  ATIVO{getSortIndicator('ticker')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('type')}`} onClick={() => requestSort('type')}>
                  TIPO{getSortIndicator('type')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('amountPerShare')}`} onClick={() => requestSort('amountPerShare')}>
                  POR COTA{getSortIndicator('amountPerShare')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('totalAmount')}`} onClick={() => requestSort('totalAmount')}>
                  TOTAL{getSortIndicator('totalAmount')}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {paginated.map((row, idx) => (
                <tr key={idx} className="hover:bg-white/5 transition-colors">
                  <td className="px-4 py-3">{formatDate(row.exDate)}</td>
                  <td className="px-4 py-3">{formatDate(row.paymentDate)}</td>
                  <td className="px-4 py-3 text-white font-medium">{row.ticker}</td>
                  <td className="px-4 py-3">
                    <span className="text-gray-300">
                      {row.type === DividendType.DIVIDENDO ? 'Dividendo' : row.type === DividendType.JCP ? 'JCP' : 'Rendimento'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">{formatCurrency(row.amountPerShare)}</td>
                  <td className="px-4 py-3 text-right text-success font-medium">{formatCurrency(row.totalAmount)}</td>
                </tr>
              ))}
              {paginated.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-500">Nenhum provento encontrado.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={setCurrentPage}
          itemsPerPage={itemsPerPage}
          onItemsPerPageChange={(size) => { setItemsPerPage(size); setCurrentPage(1); }}
          totalItems={sortedData.length}
        />
      </Card>
    </div>
  );
};
