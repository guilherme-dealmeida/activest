import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { portfolioApi } from '@/api/portfolio';
import { strategyApi } from '@/api/strategy';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { PieOfPieChart } from '@/components/charts/PieOfPieChart';
import { AllocationDetailedCharts } from '@/components/charts/AllocationDetailedCharts';
import { clsx } from 'clsx';
import { PortfolioAllocation, PortfolioAllocationDetailed, ReservationAllocation, Strategy, StrategyReservation } from '@/types';
import { INCOME_COLOR, RESERVATION_COLOR } from '@/utils/reservation';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';

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

  // Ideal de cada tipo na estratégia, para a legenda da pizza por tipo (12/10/2026). Mesma chave da tela Estratégia:
  // salvar o Ideal Estratégico já atualiza a legenda. Tipo sem ideal fica sem a coluna preenchida.
  const { data: strategy } = useQuery<Strategy[]>({
    queryKey: ['strategy'],
    queryFn: strategyApi.get
  });
  const idealByType = new Map((strategy ?? []).map(s => [s.assetTypeName, Number(s.targetPercentage)]));

  // Ideal da reserva de emergência, na legenda da rosca da reserva. idealPercentage já resolve os dois tipos: o alvo
  // no Por Porcentagem; no Por Valor, alvo ÷ carteira (limitado a 100%). Mesma chave da aba Estratégia Reserva.
  const { data: reservationStrategy } = useQuery<StrategyReservation>({
    queryKey: ['strategy', 'reservation'],
    queryFn: strategyApi.getReservation
  });

  if (loadingAlloc || loadingDetailed || loadingReservation) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  const chartData = allocation?.map(item => ({
    name: item.typeName,
    value: item.value,
    percentage: item.percentage,
    color: item.colorHex,
    ideal: idealByType.get(item.typeName)
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
          color: RESERVATION_COLOR,
          ideal: reservationStrategy ? Number(reservationStrategy.idealPercentage) : undefined
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
        // Pizza de pizza (12/10/2026): a Geração de Renda da pizza da reserva se abre na pizza por tipo, à
        // direita, ligada por duas linhas. Em tela estreita os gráficos empilham, sem as linhas.
        <Card>
          <PieOfPieChart
            expandedName="Geração de Renda"
            main={{
              data: reservationChartData,
              total: reservationAllocation?.totalValue ?? 0,
              title: 'Alocação da Reserva de Emergência',
              titleInfo: 'Quanto da carteira está na reserva hoje, comparado com a geração de renda.'
            }}
            detail={{
              data: chartData,
              total: totalValue,
              title: 'Alocação da Carteira (Por Tipo de Ativo)',
              titleInfo: 'Considera só a geração de renda: é a fatia cinza do gráfico ao lado, aberta por tipo de ativo.'
            }}
          />
        </Card>
      )}

      {activeTab === 'detalhada' && detailedAllocation && (
        <div className="space-y-4">
          <h3 className="text-lg font-medium text-white">
            <LabelWithInfo info="Distribuição dentro de cada tipo de ativo, sem a reserva de emergência.">
              Alocação por Categoria (Dentro de cada Tipo)
            </LabelWithInfo>
          </h3>
          <AllocationDetailedCharts data={detailedAllocation} />
        </div>
      )}
    </div>
  );
};
