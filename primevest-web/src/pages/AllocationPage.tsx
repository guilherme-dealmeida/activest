import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { portfolioApi } from '@/api/portfolio';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { AllocationPieChart } from '@/components/charts/AllocationPieChart';
import { AllocationDetailedCharts } from '@/components/charts/AllocationDetailedCharts';
import { clsx } from 'clsx';
import { PortfolioAllocation, PortfolioAllocationDetailed } from '@/types';

export const AllocationPage = () => {
  const [activeTab, setActiveTab] = useState<'geral' | 'detalhada'>('geral');

  const { data: allocation, isLoading: loadingAlloc } = useQuery<PortfolioAllocation[]>({
    queryKey: ['portfolio', 'allocation'],
    queryFn: portfolioApi.getAllocation
  });

  const { data: detailedAllocation, isLoading: loadingDetailed } = useQuery<PortfolioAllocationDetailed[]>({
    queryKey: ['portfolio', 'allocation', 'detailed'],
    queryFn: portfolioApi.getAllocationDetailed
  });

  if (loadingAlloc || loadingDetailed) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  const chartData = allocation?.map(item => ({
    name: item.typeName,
    value: item.value,
    percentage: item.percentage,
    color: item.colorHex
  })) || [];

  const totalValue = allocation?.reduce((acc, item) => acc + item.value, 0) || 0;

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
        <Card className="h-[600px] flex items-center justify-center">
          <AllocationPieChart 
            data={chartData} 
            totalValue={totalValue} 
            title="Alocação da Carteira (Por Tipo de Ativo)"
          />
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
