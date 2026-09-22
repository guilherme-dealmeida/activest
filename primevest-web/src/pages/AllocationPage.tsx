import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { portfolioApi } from '@/api/portfolio';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { AllocationPieChart } from '@/components/charts/AllocationPieChart';
import { AllocationDetailedCharts } from '@/components/charts/AllocationDetailedCharts';
import { clsx } from 'clsx';
import { PortfolioAllocation, PortfolioAllocationDetailed, ReservationAllocation } from '@/types';
import { INCOME_COLOR, RESERVATION_COLOR } from '@/utils/reservation';

export const AllocationPage = () => {
  const [activeTab, setActiveTab] = useState<'geral' | 'detalhada'>('geral');

  // Sem a reserva de emergência, como no rebalanceamento: ela tem o gráfico
  // próprio ao lado. Chave diferente da do Dashboard, que mostra a carteira
  // inteira pelo mesmo endpoint — sem isso as duas telas dividiriam o cache.
  const { data: allocation, isLoading: loadingAlloc } = useQuery<PortfolioAllocation[]>({
    queryKey: ['portfolio', 'allocation', 'income'],
    queryFn: () => portfolioApi.getAllocation(true)
  });

  const { data: detailedAllocation, isLoading: loadingDetailed } = useQuery<PortfolioAllocationDetailed[]>({
    queryKey: ['portfolio', 'allocation', 'detailed'],
    queryFn: portfolioApi.getAllocationDetailed
  });

  // Reserva x geração de renda, como está hoje. A chave fica sob ['portfolio']
  // para que ligar a reserva de um ativo na Carteira já atualize este gráfico.
  const { data: reservationAllocation, isLoading: loadingReservation } = useQuery<ReservationAllocation>({
    queryKey: ['portfolio', 'allocation', 'reservation'],
    queryFn: portfolioApi.getReservationAllocation
  });

  if (loadingAlloc || loadingDetailed || loadingReservation) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  const chartData = allocation?.map(item => ({
    name: item.typeName,
    value: item.value,
    percentage: item.percentage,
    color: item.colorHex
  })) || [];

  const totalValue = allocation?.reduce((acc, item) => acc + item.value, 0) || 0;

  // Fatia zerada fica de fora: sem reserva nenhuma, o gráfico é um anel cinza
  // inteiro, e não um anel com uma fatia de 0% espremida no paddingAngle.
  const reservationChartData = reservationAllocation
    ? [
        {
          name: 'Reserva de Emergência',
          value: reservationAllocation.reservationValue,
          percentage: reservationAllocation.reservationPercentage,
          color: RESERVATION_COLOR
        },
        {
          name: 'Geração de Renda',
          value: reservationAllocation.incomeValue,
          percentage: reservationAllocation.incomePercentage,
          color: INCOME_COLOR
        }
      ].filter(slice => slice.value > 0)
    : [];

  return (
    <div className="space-y-6">
      <div className="flex space-x-1 bg-surface p-1 rounded-lg w-fit border border-surface-light">
        <button
          onClick={() => setActiveTab('geral')}
          className={clsx(
            "px-4 py-2 text-sm font-medium rounded-md transition-colors",
            activeTab === 'geral' ? "bg-surface-light text-white" : "text-gray-400 hover:text-white hover:bg-surface-light/50"
          )}
        >
          Carteira de Investimentos
        </button>
        <button
          onClick={() => setActiveTab('detalhada')}
          className={clsx(
            "px-4 py-2 text-sm font-medium rounded-md transition-colors",
            activeTab === 'detalhada' ? "bg-surface-light text-white" : "text-gray-400 hover:text-white hover:bg-surface-light/50"
          )}
        >
          Por Categoria
        </button>
      </div>

      {activeTab === 'geral' && (
        // Um card, duas metades iguais: por tipo à esquerda, reserva à
        // direita. Em tela estreita os gráficos empilham.
        <Card className="lg:h-[600px]">
          <div className="grid h-full grid-cols-1 gap-6 lg:grid-cols-2">
            <div className="h-[560px] lg:h-full">
              <AllocationPieChart
                data={chartData}
                totalValue={totalValue}
                title="Alocação da Carteira (Por Tipo de Ativo)"
              />
            </div>
            <div className="h-[560px] lg:h-full">
              <AllocationPieChart
                data={reservationChartData}
                totalValue={reservationAllocation?.totalValue ?? 0}
                title="Alocação da Reserva de Emergência"
              />
            </div>
          </div>
        </Card>
      )}

      {activeTab === 'detalhada' && detailedAllocation && (
        <div className="space-y-4">
          <h3 className="text-lg font-medium text-white">Alocação por Categoria (Dentro de cada Tipo)</h3>
          <AllocationDetailedCharts data={detailedAllocation} />
        </div>
      )}
    </div>
  );
};
