import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { strategyApi } from '@/api/strategy';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { formatCurrency, formatPercentage } from '@/utils/format';
import { clsx } from 'clsx';
import { Save, Plus, Trash2, RefreshCw } from 'lucide-react';
import { Strategy, Rebalance } from '@/types';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from 'recharts';

const PIE_COLORS = ['#c9a84c', '#38a169', '#e53e3e', '#3182ce', '#805ad5', '#d69e2e', '#ed8936', '#dd6b20'];

interface AssetType {
  id: number;
  name: string;
  description: string;
  colorHex: string;
}

const CustomTooltip = ({ active, payload }: any) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    return (
      <div className="bg-surface border border-surface-light p-3 rounded-lg shadow-lg">
        <p className="font-medium text-white mb-1">{data.name}</p>
        <p className="text-sm text-gray-300">
          Percentual: <span className="text-white font-medium">{formatPercentage(data.value)}</span>
        </p>
      </div>
    );
  }
  return null;
};

export const StrategyPage = () => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'ideal' | 'rebalanceamento'>('ideal');
  // Local editing state: { assetTypeId -> { value, typeName } }
  const [editingRows, setEditingRows] = useState<Record<number, { value: string; typeName: string }>>({});
  const [selectedTypeToAdd, setSelectedTypeToAdd] = useState<string>('');

  const { data: strategy, isLoading: loadingStrategy } = useQuery<Strategy[]>({
    queryKey: ['strategy'],
    queryFn: strategyApi.get
  });

  const { data: assetTypes } = useQuery<AssetType[]>({
    queryKey: ['assetTypes'],
    queryFn: strategyApi.getAssetTypes
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

  // Sync local state from server data
  const syncFromServer = (data: Strategy[]) => {
    const rows: Record<number, { value: string; typeName: string }> = {};
    data.forEach(item => {
      rows[item.assetTypeId] = {
        value: item.targetPercentage.toString(),
        typeName: item.assetTypeName,
      };
    });
    setEditingRows(rows);
  };

  useEffect(() => {
    if (strategy) {
      syncFromServer(strategy);
    }
  }, [strategy]);

  if (loadingStrategy || loadingRebalance) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  // Asset types not in the local editing state
  const usedTypeIds = new Set(Object.keys(editingRows).map(Number));
  const availableTypes = (assetTypes || []).filter(t => !usedTypeIds.has(t.id));

  // Add type locally (no persist)
  const handleAddType = () => {
    const typeId = Number(selectedTypeToAdd);
    if (!typeId) return;
    const type = (assetTypes || []).find(t => t.id === typeId);
    if (!type) return;
    setEditingRows(prev => ({
      ...prev,
      [typeId]: { value: '0', typeName: type.description || type.name }
    }));
    setSelectedTypeToAdd('');
  };

  // Remove type locally (no persist)
  const handleRemoveType = (assetTypeId: number) => {
    setEditingRows(prev => {
      const next = { ...prev };
      delete next[assetTypeId];
      return next;
    });
  };

  // Refresh: restore from server
  const handleRefresh = () => {
    if (strategy) {
      syncFromServer(strategy);
    }
  };

  // Save: persist all local rows to backend (full replace)
  const handleSave = () => {
    const total = Object.values(editingRows).reduce((acc, row) => acc + Number(row.value), 0);
    if (Math.round(total * 10) !== 1000) {
      alert(`A soma dos percentuais deve ser 100%. Atual: ${total.toFixed(1)}%`);
      return;
    }

    mutation.mutate({
      items: Object.entries(editingRows).map(([id, row]) => ({
        assetTypeId: Number(id),
        targetPercentage: Number(row.value),
        includeReservation: false
      }))
    });
  };

  // Build display rows
  const strategyRows = Object.entries(editingRows).map(([idStr, row]) => {
    const id = Number(idStr);
    const rebalanceItem = (rebalance?.items || []).find(i => i.typeName === row.typeName);
    return {
      assetTypeId: id,
      typeName: row.typeName,
      currentPercentage: rebalanceItem?.currentPercentage || 0,
      diffPercentage: rebalanceItem ? rebalanceItem.currentPercentage - Number(row.value) : 0,
      value: row.value,
    };
  });

  // Pie chart data
  const pieData = strategyRows
    .filter(s => Number(s.value) > 0)
    .map((s, idx) => ({
      name: s.typeName,
      value: Number(s.value),
      color: PIE_COLORS[idx % PIE_COLORS.length]
    }));

  const totalPct = Object.values(editingRows).reduce((acc, row) => acc + Number(row.value), 0);

  // Totais da aba Rebalanceamento: quanto aportar somado e quanto a carteira
  // passa a valer depois desses aportes.
  const totalAportar = (rebalance?.items || []).reduce((acc, item) => acc + item.amountToRebalance, 0);
  const totalEstrategico = (rebalance?.items || []).reduce((acc, item) => acc + item.idealValue, 0);

  // Check if there are unsaved changes
  const hasChanges = (() => {
    if (!strategy) return Object.keys(editingRows).length > 0;
    const serverIds = new Set(strategy.map(s => s.assetTypeId));
    const localIds = new Set(Object.keys(editingRows).map(Number));
    if (serverIds.size !== localIds.size) return true;
    for (const s of strategy) {
      const local = editingRows[s.assetTypeId];
      if (!local) return true;
      if (Number(local.value) !== s.targetPercentage) return true;
    }
    for (const id of localIds) {
      if (!serverIds.has(id)) return true;
    }
    return false;
  })();

  return (
    <div className="space-y-6">
      {/* Tabs */}
      <div className="flex space-x-1 bg-surface p-1 rounded-lg w-fit border border-surface-light">
        <button
          onClick={() => setActiveTab('ideal')}
          className={clsx(
            "px-4 py-2 text-sm font-medium rounded-md transition-colors",
            activeTab === 'ideal' ? "bg-surface-light text-white" : "text-gray-400 hover:text-white hover:bg-surface-light/50"
          )}
        >
          Ideal Estratégico
        </button>
        <button
          onClick={() => setActiveTab('rebalanceamento')}
          className={clsx(
            "px-4 py-2 text-sm font-medium rounded-md transition-colors",
            activeTab === 'rebalanceamento' ? "bg-surface-light text-white" : "text-gray-400 hover:text-white hover:bg-surface-light/50"
          )}
        >
          Rebalanceamento
        </button>
      </div>

      {/* Tab 1: Ideal Estratégico */}
      {activeTab === 'ideal' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Tabela de percentuais */}
          <Card>
            <div className="flex items-center justify-between mb-6">
              <div>
                <h3 className="text-lg font-medium text-white">Percentuais Ideais (Estratégia)</h3>
                <p className="text-sm text-gray-400">Defina o percentual alvo para cada tipo de ativo.</p>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={handleRefresh}
                  className="p-2 text-gray-400 hover:text-white transition-colors rounded-md hover:bg-surface-light"
                  title="Restaurar do banco de dados"
                >
                  <RefreshCw className="w-4 h-4" />
                </button>
                <Button onClick={handleSave} isLoading={mutation.isPending} disabled={!hasChanges}>
                  <Save className="w-4 h-4 mr-2" />
                  Salvar
                </Button>
              </div>
            </div>

            {hasChanges && (
              <div className="mb-4 px-3 py-2 rounded-md bg-accent/10 border border-accent/20 text-sm text-accent">
                Alterações não salvas. Clique em "Salvar" para persistir.
              </div>
            )}

            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left">
                <thead className="text-xs text-gray-400 uppercase bg-surface-light border-b border-surface-light">
                  <tr>
                    <th className="px-4 py-3">Tipo Ativo</th>
                    <th className="px-4 py-3 text-center">% Atual</th>
                    <th className="px-4 py-3 text-center">% Ideal</th>
                    <th className="px-4 py-3 text-center">% Diff</th>
                    <th className="px-4 py-3 w-10"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-surface-light">
                  {strategyRows.map((row) => (
                    <tr key={row.assetTypeId} className="hover:bg-surface-light/30">
                      <td className="px-4 py-3 font-medium text-white">{row.typeName}</td>
                      <td className="px-4 py-3 text-center">{formatPercentage(row.currentPercentage)}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-center gap-2">
                          <Input
                            type="number"
                            min="0"
                            max="100"
                            step="0.1"
                            className="w-24 text-center"
                            value={row.value}
                            onChange={(e) => setEditingRows(prev => ({
                              ...prev,
                              [row.assetTypeId]: { ...prev[row.assetTypeId], value: e.target.value }
                            }))}
                          />
                          <span className="text-gray-400">%</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span className={clsx(
                          "font-medium",
                          row.diffPercentage > 0 ? "text-success" : row.diffPercentage < 0 ? "text-danger" : "text-gray-400"
                        )}>
                          {formatPercentage(Math.abs(row.diffPercentage))} {row.diffPercentage > 0 ? 'acima' : row.diffPercentage < 0 ? 'abaixo' : ''}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <button
                          onClick={() => handleRemoveType(row.assetTypeId)}
                          className="text-gray-500 hover:text-danger transition-colors p-1"
                          title="Remover tipo"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-surface-light">
                    <td className="px-4 py-3 font-medium text-white">Total</td>
                    <td className="px-4 py-3 text-center text-white font-medium">100,00%</td>
                    <td className="px-4 py-3 text-center">
                      <span className={clsx(
                        "font-semibold",
                        totalPct === 100 ? "text-success" : "text-danger"
                      )}>
                        {totalPct.toFixed(1)}%
                      </span>
                    </td>
                    <td colSpan={2}></td>
                  </tr>
                </tfoot>
              </table>
            </div>

            {/* Adicionar tipo de ativo */}
            {availableTypes.length > 0 && (
              <div className="mt-4 pt-4 border-t border-surface-light flex items-center gap-3">
                <select
                  value={selectedTypeToAdd}
                  onChange={(e) => setSelectedTypeToAdd(e.target.value)}
                  className="flex-1 h-10 rounded-md border border-surface-light bg-surface px-3 py-2 text-sm text-white focus:ring-2 focus:ring-accent focus:border-transparent"
                >
                  <option value="">Selecione um tipo de ativo...</option>
                  {availableTypes.map(t => (
                    <option key={t.id} value={t.id}>{t.description || t.name}</option>
                  ))}
                </select>
                <Button onClick={handleAddType} disabled={!selectedTypeToAdd}>
                  <Plus className="w-4 h-4 mr-2" />
                  Adicionar
                </Button>
              </div>
            )}
          </Card>

          {/* Gráfico de Pizza */}
          <Card>
            <h3 className="text-lg font-medium text-white mb-4 text-center">Distribuição Estratégica</h3>
            {pieData.length > 0 ? (
              <div className="h-[400px] w-full relative">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={pieData}
                      cx="50%"
                      cy="50%"
                      innerRadius="55%"
                      outerRadius="80%"
                      paddingAngle={2}
                      dataKey="value"
                      stroke="none"
                    >
                      {pieData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip content={<CustomTooltip />} />
                    <Legend
                      verticalAlign="bottom"
                      height={36}
                      formatter={(value, entry: any) => (
                        <span className="text-gray-300 ml-1">{value} ({formatPercentage(entry.payload.value)})</span>
                      )}
                    />
                  </PieChart>
                </ResponsiveContainer>
                <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none pb-8">
                  <span className="text-sm text-gray-400">Total</span>
                  <span className="text-lg font-semibold text-white">{totalPct.toFixed(1)}%</span>
                </div>
              </div>
            ) : (
              <div className="h-[400px] flex items-center justify-center text-gray-500">
                <p>Defina os percentuais ideais para visualizar o gráfico</p>
              </div>
            )}
          </Card>
        </div>
      )}

      {/* Tab 2: Rebalanceamento */}
      {activeTab === 'rebalanceamento' && (
        <Card title="Sugestão de Aporte (Rebalanceamento)">
          <div className="overflow-x-auto mt-4">
            <table className="w-full text-sm text-left">
              <thead className="text-xs text-gray-400 uppercase bg-surface-light border-b border-surface-light">
                <tr>
                  <th className="px-4 py-3">Tipo Ativo</th>
                  <th className="px-4 py-3 text-right">% Ideal</th>
                  <th className="px-4 py-3 text-right">Aportado</th>
                  <th className="px-4 py-3 text-right">Aportar</th>
                  <th className="px-4 py-3 text-right">Estratégico</th>
                  <th className="px-4 py-3 text-right">% Carteira</th>
                  <th className="px-4 py-3 text-right">% Diff</th>
                  <th className="px-4 py-3 text-center">Sugestão</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-light">
                {rebalance?.items.map((item, idx) => {
                  // Valor que o tipo passa a ter depois do aporte sugerido:
                  // vem do backend, que ja aplica a regra do tipo mais acima do ideal.
                  const estrategico = item.idealValue;
                  return (
                    <tr key={idx} className="hover:bg-surface-light/30">
                      <td className="px-4 py-3 font-medium text-white">{item.typeName}</td>
                      <td className="px-4 py-3 text-right">{formatPercentage(item.idealPercentage)}</td>
                      <td className="px-4 py-3 text-right">{formatCurrency(item.currentValue)}</td>
                      <td className="px-4 py-3 text-right font-medium">
                        <span className={item.amountToRebalance > 0 ? "text-success" : item.amountToRebalance < 0 ? "text-danger" : "text-gray-400"}>
                          {formatCurrency(item.amountToRebalance)}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right font-medium text-white">
                        {formatCurrency(estrategico)}
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
                  );
                })}
              </tbody>
              {rebalance && (
                <tfoot>
                  <tr className="border-t border-surface-light">
                    <td className="px-4 py-3 font-semibold text-white">Total</td>
                    <td className="px-4 py-3 text-right font-medium text-white">100,00%</td>
                    <td className="px-4 py-3 text-right font-semibold text-white">{formatCurrency(rebalance.totalPortfolioValue)}</td>
                    <td className="px-4 py-3 text-right font-semibold text-success">{formatCurrency(totalAportar)}</td>
                    <td className="px-4 py-3 text-right font-semibold text-white">{formatCurrency(totalEstrategico)}</td>
                    <td colSpan={3}></td>
                  </tr>
                </tfoot>
              )}
            </table>
          </div>
        </Card>
      )}
    </div>
  );
};
