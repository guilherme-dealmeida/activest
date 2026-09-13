import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { strategyApi } from '@/api/strategy';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { formatCurrency, formatPercentage } from '@/utils/format';
import { clsx } from 'clsx';
import { Save, RefreshCw } from 'lucide-react';
import { Strategy, Rebalance } from '@/types';

export const StrategyPage = () => {
  const queryClient = useQueryClient();
  const [editingValues, setEditingValues] = useState<Record<number, string>>({});
  
  const { data: strategy, isLoading: loadingStrategy } = useQuery<Strategy[]>({
    queryKey: ['strategy'],
    queryFn: strategyApi.get
  });

  const { data: rebalance, isLoading: loadingRebalance } = useQuery<Rebalance>({
    queryKey: ['strategy', 'rebalance'],
    queryFn: strategyApi.getRebalance
  });

  const mutation = useMutation({
    mutationFn: strategyApi.update,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['strategy'] });
      queryClient.invalidateQueries({ queryKey: ['strategy', 'rebalance'] });
    }
  });

  useEffect(() => {
    if (strategy) {
      const initial: Record<number, string> = {};
      strategy.forEach(item => {
        initial[item.assetTypeId] = item.targetPercentage.toString();
      });
      setEditingValues(initial);
    }
  }, [strategy]);

  if (loadingStrategy || loadingRebalance) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  const handleSave = () => {
    const total = Object.values(editingValues).reduce((acc, val) => acc + Number(val), 0);
    if (total !== 100) {
      alert(`A soma dos percentuais deve ser 100%. Atual: ${total}%`);
      return;
    }

    mutation.mutate({
      items: Object.entries(editingValues).map(([id, val]) => ({
        assetTypeId: Number(id),
        targetPercentage: Number(val),
        includeReservation: false // Defaulting for simplicity
      }))
    });
  };

  return (
    <div className="space-y-6">
      <Card>
        <div className="flex items-center justify-between mb-6">
          <div>
            <h3 className="text-lg font-medium text-white">Percentuais Ideais (Estratégia)</h3>
            <p className="text-sm text-gray-400">Defina o percentual alvo para cada tipo de ativo na sua carteira.</p>
          </div>
          <Button onClick={handleSave} isLoading={mutation.isPending}>
            <Save className="w-4 h-4 mr-2" />
            Salvar Estratégia
          </Button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-gray-400 uppercase bg-surface-light border-b border-surface-light">
              <tr>
                <th className="px-4 py-3">Tipo Ativo</th>
                <th className="px-4 py-3 text-center">% Atual</th>
                <th className="px-4 py-3 text-center">% Ideal</th>
                <th className="px-4 py-3 text-center">% Diff</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-light">
              {rebalance?.items.map((item, idx) => (
                <tr key={idx} className="hover:bg-surface-light/30">
                  <td className="px-4 py-3 font-medium text-white">{item.typeName}</td>
                  <td className="px-4 py-3 text-center">{formatPercentage(item.currentPercentage)}</td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-center gap-2">
                      <Input 
                        type="number"
                        min="0"
                        max="100"
                        step="0.1"
                        className="w-24 text-center"
                        value={strategy?.find(s => s.assetTypeName === item.typeName) ? editingValues[strategy.find(s => s.assetTypeName === item.typeName)!.assetTypeId] || '' : ''}
                        onChange={(e) => {
                          const s = strategy?.find(s => s.assetTypeName === item.typeName);
                          if(s) setEditingValues(prev => ({...prev, [s.assetTypeId]: e.target.value}))
                        }}
                      />
                      <span className="text-gray-400">%</span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <span className={clsx(
                      "font-medium",
                      item.diffPercentage > 0 ? "text-success" : item.diffPercentage < 0 ? "text-danger" : "text-gray-400"
                    )}>
                      {formatPercentage(Math.abs(item.diffPercentage))} {item.diffPercentage > 0 ? 'acima' : item.diffPercentage < 0 ? 'abaixo' : ''}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card title="Sugestão de Aporte (Rebalanceamento)">
        <div className="overflow-x-auto mt-4">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-gray-400 uppercase bg-surface-light border-b border-surface-light">
              <tr>
                <th className="px-4 py-3">Tipo Ativo</th>
                <th className="px-4 py-3 text-right">% Ideal</th>
                <th className="px-4 py-3 text-right">Aportado</th>
                <th className="px-4 py-3 text-right">Aportar</th>
                <th className="px-4 py-3 text-right">% Carteira</th>
                <th className="px-4 py-3 text-right">% Diff</th>
                <th className="px-4 py-3 text-center">Estratégico</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-light">
              {rebalance?.items.map((item, idx) => (
                <tr key={idx} className="hover:bg-surface-light/30">
                  <td className="px-4 py-3 font-medium text-white">{item.typeName}</td>
                  <td className="px-4 py-3 text-right">{formatPercentage(item.idealPercentage)}</td>
                  <td className="px-4 py-3 text-right">{formatCurrency(item.currentValue)}</td>
                  <td className="px-4 py-3 text-right font-medium">
                    <span className={item.amountToRebalance > 0 ? "text-success" : "text-gray-400"}>
                      {formatCurrency(Math.max(0, item.amountToRebalance))}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">{formatPercentage(item.currentPercentage)}</td>
                  <td className="px-4 py-3 text-right">
                    <span className={item.diffPercentage > 0 ? "text-success" : item.diffPercentage < 0 ? "text-danger" : "text-gray-400"}>
                      {formatPercentage(item.diffPercentage)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <span className={clsx(
                      "px-2.5 py-1 rounded-full text-xs font-medium border",
                      item.amountToRebalance > 0 ? "bg-success/20 text-success border-success/30" : "bg-surface-light text-gray-300 border-gray-700"
                    )}>
                      {item.amountToRebalance > 0 ? 'APORTAR' : 'AGUARDAR'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};
