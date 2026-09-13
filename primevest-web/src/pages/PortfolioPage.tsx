import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { portfolioApi } from '@/api/portfolio';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Pagination } from '@/components/ui/Pagination';
import { useSortable } from '@/hooks/useSortable';
import { formatCurrency, formatPercentage, formatShares, getReturnColor } from '@/utils/format';

export const PortfolioPage = () => {
  const [filterType, setFilterType] = useState<string>('ALL');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [itemsPerPage, setItemsPerPage] = useState<number>(10);

  const { data: allAssets = [], isLoading } = useQuery({
    queryKey: ['portfolio', 'assets'],
    queryFn: portfolioApi.getAssets,
  });

  const filteredAssets = allAssets.filter(
    (a) => filterType === 'ALL' || a.typeName === filterType
  );

  const { sortedData, requestSort, getSortIndicator, getSortClass } =
    useSortable(filteredAssets);

  const paginatedAssets = sortedData.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  const totalInvested = filteredAssets.reduce((sum, a) => sum + a.totalInvested, 0);
  const totalCurrent = filteredAssets.reduce((sum, a) => sum + a.currentValue, 0);
  const totalDividends = filteredAssets.reduce((sum, a) => sum + a.totalDividends, 0);
  const totalValorization = filteredAssets.reduce((sum, a) => sum + a.valorization, 0);

  const assetTypes = [...new Set(allAssets.map((a) => a.typeName))];

  if (isLoading) {
    return <LoadingSpinner />;
  }

  return (
    <div className="bg-surface rounded-lg border border-white/10 p-6">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-semibold">Portfólio</h1>
        <select
          value={filterType}
          onChange={(e) => {
            setFilterType(e.target.value);
            setCurrentPage(1);
          }}
          className="bg-surface border border-white/10 rounded-md px-3 py-1.5 text-sm"
        >
          <option value="ALL">Todos os tipos</option>
          {assetTypes.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-white/10">
              <th
                className={`text-left py-3 px-3 font-medium text-xs cursor-pointer ${getSortClass('typeName')}`}
                onClick={() => requestSort('typeName')}
              >
                TIPO ATIVO{getSortIndicator('typeName')}
              </th>
              <th
                className={`text-left py-3 px-3 font-medium text-xs cursor-pointer ${getSortClass('categoryName')}`}
                onClick={() => requestSort('categoryName')}
              >
                CATEGORIA{getSortIndicator('categoryName')}
              </th>
              <th
                className={`text-left py-3 px-3 font-medium text-xs cursor-pointer ${getSortClass('ticker')}`}
                onClick={() => requestSort('ticker')}
              >
                ATIVO{getSortIndicator('ticker')}
              </th>
              <th
                className={`text-right py-3 px-3 font-medium text-xs cursor-pointer ${getSortClass('totalShares')}`}
                onClick={() => requestSort('totalShares')}
              >
                COTAS{getSortIndicator('totalShares')}
              </th>
              <th
                className={`text-right py-3 px-3 font-medium text-xs cursor-pointer ${getSortClass('totalInvested')}`}
                onClick={() => requestSort('totalInvested')}
              >
                VALOR INICIAL{getSortIndicator('totalInvested')}
              </th>
              <th
                className={`text-right py-3 px-3 font-medium text-xs cursor-pointer ${getSortClass('currentValue')}`}
                onClick={() => requestSort('currentValue')}
              >
                VALOR ATUAL{getSortIndicator('currentValue')}
              </th>
              <th
                className={`text-right py-3 px-3 font-medium text-xs cursor-pointer ${getSortClass('totalDividends')}`}
                onClick={() => requestSort('totalDividends')}
              >
                PROVENTOS{getSortIndicator('totalDividends')}
              </th>
              <th
                className={`text-right py-3 px-3 font-medium text-xs cursor-pointer ${getSortClass('valorization')}`}
                onClick={() => requestSort('valorization')}
              >
                VALORIZAÇÃO{getSortIndicator('valorization')}
              </th>
              <th
                className={`text-right py-3 px-3 font-medium text-xs cursor-pointer ${getSortClass('returnPercentage')}`}
                onClick={() => requestSort('returnPercentage')}
              >
                RENT. %{getSortIndicator('returnPercentage')}
              </th>
            </tr>
          </thead>
          <tbody>
            {paginatedAssets.map((asset) => (
              <tr
                key={asset.ticker}
                className="border-b border-white/5 hover:bg-white/5 transition-colors"
              >
                <td className="py-3 px-3">{asset.typeName}</td>
                <td className="py-3 px-3">{asset.categoryName}</td>
                <td className="py-3 px-3">
                  <div className="flex flex-col">
                    <span className="font-medium">{asset.ticker}</span>
                    <span className="text-xs text-white/50">{asset.assetName}</span>
                  </div>
                </td>
                <td className="py-3 px-3 text-right">{formatShares(asset.totalShares)}</td>
                <td className="py-3 px-3 text-right">{formatCurrency(asset.totalInvested)}</td>
                <td className="py-3 px-3 text-right">{formatCurrency(asset.currentValue)}</td>
                <td className="py-3 px-3 text-right">{formatCurrency(asset.totalDividends)}</td>
                <td className="py-3 px-3 text-right">
                  <span className={getReturnColor(asset.valorization)}>
                    {formatCurrency(asset.valorization)}
                  </span>
                </td>
                <td className="py-3 px-3 text-right">
                  <span className={getReturnColor(asset.returnPercentage)}>
                    {formatPercentage(asset.returnPercentage)}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-white/10 font-semibold">
              <td className="py-3 px-3" colSpan={4}>
                Totais
              </td>
              <td className="py-3 px-3 text-right">{formatCurrency(totalInvested)}</td>
              <td className="py-3 px-3 text-right">{formatCurrency(totalCurrent)}</td>
              <td className="py-3 px-3 text-right">{formatCurrency(totalDividends)}</td>
              <td className="py-3 px-3 text-right">
                <span className={getReturnColor(totalValorization)}>
                  {formatCurrency(totalValorization)}
                </span>
              </td>
              <td className="py-3 px-3 text-right">
                <span className={getReturnColor(totalInvested > 0 ? ((totalCurrent + totalDividends - totalInvested) / totalInvested) * 100 : 0)}>
                  {formatPercentage(totalInvested > 0 ? ((totalCurrent + totalDividends - totalInvested) / totalInvested) * 100 : 0)}
                </span>
              </td>
            </tr>
          </tfoot>
        </table>
      </div>

      <Pagination
        currentPage={currentPage}
        totalPages={Math.ceil(sortedData.length / itemsPerPage)}
        totalItems={sortedData.length}
        itemsPerPage={itemsPerPage}
        onPageChange={setCurrentPage}
        onItemsPerPageChange={(size) => {
          setItemsPerPage(size);
          setCurrentPage(1);
        }}
      />
    </div>
  );
};
