import { useQuery } from '@tanstack/react-query';
import { portfolioApi } from '@/api/portfolio';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { formatCurrency, formatPercentage, getReturnColor } from '@/utils/format';
import { Wallet, TrendingUp, PiggyBank, DollarSign } from 'lucide-react';
import { AllocationPieChart } from '@/components/charts/AllocationPieChart';

export const DashboardPage = () => {
  const { data: summary, isLoading: loadingSummary } = useQuery({
    queryKey: ['portfolio', 'summary'],
    queryFn: portfolioApi.getSummary
  });

  const { data: allocation, isLoading: loadingAllocation } = useQuery({
    queryKey: ['portfolio', 'allocation'],
    // Carteira inteira, reserva incluída. Embrulhado numa arrow porque o
    // React Query passa o próprio contexto como primeiro argumento, e ele
    // chegaria em getAllocation como excludeReservation.
    queryFn: () => portfolioApi.getAllocation()
  });

  const { data: assets, isLoading: loadingAssets } = useQuery({
    queryKey: ['portfolio', 'assets'],
    queryFn: portfolioApi.getAssets
  });

  if (loadingSummary || loadingAllocation || loadingAssets) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner /></div>;
  }

  const topAssets = assets ? [...assets].sort((a, b) => b.currentValue - a.currentValue).slice(0, 5) : [];

  const chartData = (allocation || []).map(item => ({
    name: item.typeName,
    value: item.value,
    percentage: item.percentage,
    color: item.colorHex
  }));

  return (
    <div className="space-y-6">
      {/* Top Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
        <Card 
          title="Total Investido" 
          value={formatCurrency(summary?.totalInvested || 0)}
          icon={<PiggyBank className="h-5 w-5" />}
        />
        <Card 
          title="Valor Atual" 
          value={formatCurrency(summary?.currentValue || 0)}
          icon={<Wallet className="h-5 w-5" />}
        />
        <Card 
          title="Rentabilidade" 
          value={<span className={getReturnColor(summary?.returnPercentage || 0)}>{formatPercentage(summary?.returnPercentage || 0)}</span>}
          icon={<TrendingUp className="h-5 w-5" />}
          description={<span className={getReturnColor(summary?.totalReturn || 0)}>{formatCurrency(summary?.totalReturn || 0)}</span>}
        />
        <Card 
          title="Proventos Totais" 
          value={formatCurrency(summary?.totalDividends || 0)}
          icon={<DollarSign className="h-5 w-5" />}
        />
      </div>

      {/* Charts & Tables */}
      <div className="grid grid-cols-1 xl:grid-cols-5 gap-6">
        <div className="xl:col-span-2 bg-surface rounded-lg border border-white/10 p-6">
          <h3 className="text-lg font-semibold text-white mb-4">Alocação da Carteira</h3>
          {/* Sem altura fixa: rosca centralizada e legenda no canto inferior direito (CornerLegendDonut, 12/10/2026). */}
          <div>
            <AllocationPieChart 
              data={chartData} 
              totalValue={summary?.currentValue || 0}
              legend="corner"
            />
          </div>
        </div>
        
        <div className="xl:col-span-3 bg-surface rounded-lg border border-white/10 p-6">
          <h3 className="text-lg font-semibold text-white mb-4">Top 5 Ativos</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-white/10">
                  <th className="text-left py-3 px-4 text-gray-400 font-medium">Ativo</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium">Tipo</th>
                  <th className="text-right py-3 px-4 text-gray-400 font-medium">Valor Atual</th>
                  <th className="text-right py-3 px-4 text-gray-400 font-medium">Rentabilidade</th>
                </tr>
              </thead>
              <tbody>
                {topAssets.map((asset) => (
                  <tr key={asset.id} className="border-b border-white/5 hover:bg-white/5 transition-colors">
                    <td className="py-3 px-4">
                      <div>
                        <span className="text-white font-medium">{asset.ticker}</span>
                        <p className="text-gray-500 text-xs">{asset.assetName}</p>
                      </div>
                    </td>
                    <td className="py-3 px-4 text-gray-400">{asset.typeName}</td>
                    <td className="py-3 px-4 text-right text-white">{formatCurrency(asset.currentValue)}</td>
                    <td className={`py-3 px-4 text-right font-medium ${getReturnColor(asset.returnPercentage)}`}>
                      {formatPercentage(asset.returnPercentage)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};
