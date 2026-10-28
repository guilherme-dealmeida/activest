import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { strategyApi } from '@/api/strategy';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { formatPercentage } from '@/utils/format';
import { clsx } from 'clsx';
import { Save, Plus, Trash2, RefreshCw } from 'lucide-react';
import { Strategy, Rebalance } from '@/types';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { ChartLegend } from '@/components/charts/ChartLegend';
import { byValueDesc, DONUT_END_ANGLE, DONUT_START_ANGLE } from '@/components/charts/donut';
import { ReservationStrategyTab } from '@/components/strategy/ReservationStrategyTab';
import { STRATEGY_HELP, restoredMessage } from '@/components/strategy/strategyHelp';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';
import { TABLE_ACTION_CLASS } from '@/components/ui/tableAction';
import { PageLink } from '@/components/ui/PageLink';
import { isStrategyTotalValid, strategyTotal, strategyTotalLabel } from '@/utils/strategyRules';

const PIE_COLORS = ['#c9a84c', '#38a169', '#e53e3e', '#3182ce', '#805ad5', '#d69e2e', '#ed8936', '#dd6b20'];

interface AssetType {
  id: number;
  name: string;
  description: string;
  colorHex: string;
}

// Fatia do gráfico de pizza, como vem no payload do Recharts.
interface PieSlice {
  name: string;
  value: number;
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: { payload: PieSlice }[];
}

const CustomTooltip = ({ active, payload }: CustomTooltipProps) => {
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
  const { showToast } = useToast();
  // Aba na URL (`?aba=reserva`, 17/10/2026): o Rebalanceamento leva direto a cada uma.
  const [params, setParams] = useSearchParams();
  const activeTab: 'ideal' | 'reservation' = params.get('tab') === 'reservation' ? 'reservation' : 'ideal';
  const setActiveTab = (tab: 'ideal' | 'reservation') => {
    const next = new URLSearchParams(params);
    if (tab === 'reservation') next.set('tab', 'reservation'); else next.delete('tab');
    setParams(next, { replace: true });
  };
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

  // O rebalanceamento virou tela própria (RebalancePage, 11/10/2026), mas o Ideal Estratégico ainda lê dele o
  // % Atual de cada tipo.
  const { data: rebalance, isLoading: loadingRebalance } = useQuery<Rebalance>({
    queryKey: ['strategy', 'rebalance'],
    queryFn: strategyApi.getRebalance
  });

  const mutation = useMutation({
    mutationFn: strategyApi.update,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['strategy'] });
      queryClient.invalidateQueries({ queryKey: ['strategy', 'rebalance'] });
      showToast('Estratégia salva.', 'success');
    },
    onError: (error: unknown) => {
      showToast(apiMessage(error) ?? 'Não foi possível salvar a estratégia.', 'error');
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
      showToast(restoredMessage(hasChanges), 'info');
    }
  };

  // Save: persist all local rows to backend (full replace)
  const handleSave = () => {
    // Soma em centésimos e arredondada a uma casa, como o backend (utils/strategyRules.ts; PLANO_TESTES_FRONT, D2)
    const values = Object.values(editingRows).map(row => row.value);
    if (!isStrategyTotalValid(values)) {
      showToast(
        `A soma dos percentuais deve ser 100%. Hoje está em ${strategyTotalLabel(values)}%.`,
        'error'
      );
      return;
    }

    mutation.mutate({
      items: Object.entries(editingRows).map(([id, row]) => ({
        assetTypeId: Number(id),
        targetPercentage: Number(row.value)
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

  // Pie chart data. A cor e a cadastrada no tipo de ativo (asset_types.color_hex);
  // a paleta local so cobre tipo sem cor cadastrada.
  // Regra das roscas (donut.ts): do maior para o menor, a partir do topo. A cor da paleta local é escolhida antes de
  // ordenar, para não mudar a cor de um tipo enquanto o percentual é digitado.
  const pieData = byValueDesc(strategyRows
    .filter(s => Number(s.value) > 0)
    .map((s, idx) => ({
      name: s.typeName,
      value: Number(s.value),
      color: (assetTypes || []).find(t => t.id === s.assetTypeId)?.colorHex
        || PIE_COLORS[idx % PIE_COLORS.length]
    })));

  const totalValues = Object.values(editingRows).map(row => row.value);
  const totalPct = strategyTotal(totalValues);
  const totalValid = isStrategyTotalValid(totalValues);

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
      {/* Abas à esquerda; à direita, o link para o Rebalanceamento (tela própria desde 11/10/2026) */}
      <div className="flex flex-wrap items-center justify-between gap-4">
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
          onClick={() => setActiveTab('reservation')}
          className={clsx(
            "px-4 py-2 text-sm font-medium rounded-md transition-colors",
            activeTab === 'reservation' ? "bg-surface-light text-white" : "text-gray-400 hover:text-white hover:bg-surface-light/50"
          )}
        >
          Estratégia Reserva
        </button>
      </div>
      <div className="ml-auto"><PageLink to="/rebalance" label="Ir para Rebalanceamento" /></div>
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
                    <th className="px-4 py-3 text-center"><LabelWithInfo info={STRATEGY_HELP.currentPercentage}>% Atual</LabelWithInfo></th>
                    <th className="px-4 py-3 text-center"><LabelWithInfo info={STRATEGY_HELP.idealPercentage}>% Ideal</LabelWithInfo></th>
                    <th className="px-4 py-3 text-center"><LabelWithInfo info={STRATEGY_HELP.diffPercentage}>% Diff</LabelWithInfo></th>
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
                          {/* O Input vem embrulhado num div w-full; sem esta caixa
                              de largura fixa o "%" era empurrado para longe dele. */}
                          <div className="w-24">
                            <Input
                              type="number"
                              min="0"
                              max="100"
                              step="0.1"
                              className="text-center"
                              value={row.value}
                              onChange={(e) => setEditingRows(prev => ({
                                ...prev,
                                [row.assetTypeId]: { ...prev[row.assetTypeId], value: e.target.value }
                              }))}
                            />
                          </div>
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
                          className={TABLE_ACTION_CLASS}
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
                        totalValid ? "text-success" : "text-danger"
                      )}>
                        {formatPercentage(totalPct)}
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
              <>
              <div className="h-[340px] w-full relative">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={pieData}
                      cx="50%"
                      cy="50%"
                      innerRadius="55%"
                      outerRadius="80%"
                      startAngle={DONUT_START_ANGLE}
                      endAngle={DONUT_END_ANGLE}
                      paddingAngle={2}
                      dataKey="value"
                      stroke="none"
                    >
                      {pieData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip content={<CustomTooltip />} />
                  </PieChart>
                </ResponsiveContainer>
                <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                  <span className="text-sm text-gray-400">Total</span>
                  <span className="text-lg font-semibold text-white">{formatPercentage(totalPct)}</span>
                </div>
              </div>
              <ChartLegend
                className="mt-3"
                items={pieData.map(d => ({ name: d.name, color: d.color, value: formatPercentage(d.value) }))}
              />
              </>
            ) : (
              <div className="h-[400px] flex items-center justify-center text-gray-500">
                <p>Defina os percentuais ideais para visualizar o gráfico</p>
              </div>
            )}
          </Card>
        </div>
      )}

      {/* Tab 2: Estratégia Reserva */}
      {activeTab === 'reservation' && <ReservationStrategyTab />}
    </div>
  );
};
