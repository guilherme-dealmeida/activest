import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { clsx } from 'clsx';
import { RefreshCw, Save } from 'lucide-react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { ChartLegend } from '@/components/charts/ChartLegend';
import { byValueDesc, DONUT_END_ANGLE, DONUT_START_ANGLE } from '@/components/charts/donut';
import { strategyApi } from '@/api/strategy';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { CurrencyInput } from '@/components/ui/CurrencyInput';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { PercentInput } from '@/components/ui/PercentInput';
import { useToast } from '@/context/ToastContext';
import { ReservationType, StrategyReservation } from '@/types';
import { formatCurrency, formatPercentage } from '@/utils/format';
import { apiMessage } from '@/utils/apiError';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { STRATEGY_HELP, restoredMessage } from './strategyHelp';
import { INCOME_COLOR, RESERVATION_COLOR, RESERVATION_TYPE_LABELS } from '@/utils/reservation';

interface Draft {
  type: ReservationType;
  percentage: number;
  value: number;
}

const draftFrom = (s: StrategyReservation): Draft => ({
  type: s.typeReservation,
  percentage: s.targetPercentage ?? 0,
  value: s.targetValue ?? 0,
});

interface SliceTooltipProps {
  active?: boolean;
  payload?: { payload: { name: string; value: number } }[];
  asCurrency: boolean;
}

const SliceTooltip = ({ active, payload, asCurrency }: SliceTooltipProps) => {
  if (!active || !payload?.length) return null;
  const slice = payload[0].payload;
  return (
    <div className="bg-surface border border-surface-light p-3 rounded-lg shadow-lg">
      <p className="font-medium text-white mb-1">{slice.name}</p>
      <p className="text-sm text-gray-300">
        {asCurrency ? 'Valor' : 'Percentual'}:{' '}
        <span className="text-white font-medium">
          {asCurrency ? formatCurrency(slice.value) : formatPercentage(slice.value)}
        </span>
      </p>
    </div>
  );
};

/**
 * Aba "Estratégia Reserva".
 *
 * A reserva de emergência concorre com o resto da carteira: as posições
 * marcadas como reserva somam um bloco, e o restante é a geração de renda. O
 * alvo é um percentual da carteira inteira ou um valor em reais (regra C).
 *
 * Mesmo modelo da aba Ideal Estratégico: edita em memória, Salvar persiste,
 * ↻ volta ao que está no banco. % Ideal e % Diff reagem ao que está sendo
 * digitado, antes de salvar — o % Atual vem do servidor e não muda com isso.
 */
export const ReservationStrategyTab = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [draft, setDraft] = useState<Draft | null>(null);

  const { data: reservation, isLoading } = useQuery<StrategyReservation>({
    queryKey: ['strategy', 'reservation'],
    queryFn: strategyApi.getReservation,
  });

  useEffect(() => {
    if (reservation) setDraft(draftFrom(reservation));
  }, [reservation]);

  const mutation = useMutation({
    mutationFn: strategyApi.updateReservation,
    onSuccess: () => {
      // Prefixo ['strategy']: a própria reserva e a linha dela no rebalanceamento.
      queryClient.invalidateQueries({ queryKey: ['strategy'] });
      showToast('Estratégia de reserva de emergência salva.', 'success');
    },
    onError: (error: unknown) => {
      showToast(apiMessage(error) ?? 'Não foi possível salvar a estratégia de reserva.', 'error');
    },
  });

  if (isLoading || !reservation || !draft) {
    return (
      <div className="flex h-64 items-center justify-center">
        <LoadingSpinner className="h-8 w-8 text-accent" />
      </div>
    );
  }

  const total = reservation.totalPortfolioValue;
  const isValue = draft.type === 'VALUE';

  // O que o % Diff compara. No tipo por valor é o alvo sobre a carteira de
  // hoje, limitado a 100% — a mesma conta do backend.
  const idealPercentage = isValue
    ? total > 0 ? Math.min(100, (draft.value / total) * 100) : 0
    : draft.percentage;
  const diff = reservation.currentPercentage - idealPercentage;

  const saved = draftFrom(reservation);
  const hasChanges =
    draft.type !== saved.type ||
    (isValue ? draft.value !== saved.value : draft.percentage !== saved.percentage);

  const handleSave = () => {
    if (!isValue && draft.percentage > 100) {
      showToast('O percentual ideal da reserva não pode passar de 100%.', 'error');
      return;
    }
    mutation.mutate({
      typeReservation: draft.type,
      // Só o alvo do tipo escolhido vale; o backend descarta o outro.
      targetPercentage: isValue ? null : draft.percentage,
      targetValue: isValue ? draft.value : null,
    });
  };

  // Distribuição ideal. Por porcentagem, em %; por valor, em reais — a
  // geração de renda é o que sobra da carteira depois do alvo, nunca negativa.
  const pieData = isValue
    ? [
        { name: 'Reserva de Emergência', value: draft.value, color: RESERVATION_COLOR },
        { name: 'Geração de Renda', value: Math.max(0, total - draft.value), color: INCOME_COLOR },
      ]
    : [
        { name: 'Reserva de Emergência', value: draft.percentage, color: RESERVATION_COLOR },
        { name: 'Geração de Renda', value: Math.max(0, 100 - draft.percentage), color: INCOME_COLOR },
      ];
  // Regra das roscas (donut.ts): do maior para o menor, a partir do topo.
  const visibleSlices = byValueDesc(pieData.filter((d) => d.value > 0));
  const formatSlice = (v: number) => (isValue ? formatCurrency(v) : formatPercentage(v));

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <Card>
        <div className="flex items-center justify-between mb-6">
          <div>
            <h3 className="text-lg font-medium text-white">Estratégia de Reserva de Emergência</h3>
            <p className="text-sm text-gray-400">
              Defina quanto da carteira deve estar na reserva de emergência.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => {
                setDraft(draftFrom(reservation));
                showToast(restoredMessage(hasChanges), 'info');
              }}
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
                <th className="px-4 py-3"><LabelWithInfo info={STRATEGY_HELP.reservationType}>Tipo de Estratégia</LabelWithInfo></th>
                <th className="px-4 py-3 text-center"><LabelWithInfo info={STRATEGY_HELP.currentPercentage}>% Atual</LabelWithInfo></th>
                <th className="px-4 py-3 text-center"><LabelWithInfo info={STRATEGY_HELP.idealPercentage}>% Ideal</LabelWithInfo></th>
                <th className="px-4 py-3 text-center"><LabelWithInfo info={STRATEGY_HELP.diffPercentage}>% Diff</LabelWithInfo></th>
              </tr>
            </thead>
            <tbody>
              <tr className="hover:bg-surface-light/30">
                <td className="px-4 py-3">
                  <select
                    value={draft.type}
                    onChange={(e) => setDraft({ ...draft, type: e.target.value as ReservationType })}
                    className="h-10 w-full min-w-[10rem] rounded-md border border-surface-light bg-surface px-3 py-2 text-sm text-white focus:ring-2 focus:ring-accent focus:border-transparent"
                  >
                    {(Object.keys(RESERVATION_TYPE_LABELS) as ReservationType[]).map((t) => (
                      <option key={t} value={t}>
                        {RESERVATION_TYPE_LABELS[t]}
                      </option>
                    ))}
                  </select>
                </td>
                <td className="px-4 py-3 text-center">{formatPercentage(reservation.currentPercentage)}</td>
                <td className="px-4 py-3">
                  <div className="flex justify-center">
                    {/* "%" à direita no percentual, "R$" à esquerda no valor —
                        os dois sobre o MaskedNumberInput. */}
                    {isValue ? (
                      <div className="w-40">
                        <CurrencyInput
                          value={draft.value}
                          onChange={(v) => setDraft({ ...draft, value: v })}
                          aria-label="Valor ideal da reserva de emergência"
                        />
                      </div>
                    ) : (
                      <div className="w-28">
                        <PercentInput
                          value={draft.percentage}
                          onChange={(v) => setDraft({ ...draft, percentage: v })}
                          aria-label="Percentual ideal da reserva de emergência"
                        />
                      </div>
                    )}
                  </div>
                </td>
                <td className="px-4 py-3 text-center">
                  <span
                    className={clsx(
                      'font-medium',
                      diff > 0 ? 'text-success' : diff < 0 ? 'text-danger' : 'text-gray-400'
                    )}
                  >
                    {formatPercentage(Math.abs(diff))} {diff > 0 ? 'acima' : diff < 0 ? 'abaixo' : ''}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="mt-4 pt-4 border-t border-surface-light grid grid-cols-3 gap-4 text-sm">
          <div>
            <p className="text-gray-400">Reserva de emergência</p>
            <p className="font-medium text-white">{formatCurrency(reservation.reservationValue)}</p>
          </div>
          <div>
            <p className="text-gray-400">Geração de renda</p>
            <p className="font-medium text-white">{formatCurrency(reservation.incomeValue)}</p>
          </div>
          <div>
            <p className="text-gray-400">Carteira</p>
            <p className="font-medium text-white">{formatCurrency(total)}</p>
          </div>
        </div>
      </Card>

      <Card>
        <h3 className="text-lg font-medium text-white mb-4 text-center">Distribuição Estratégica Reserva</h3>
        {visibleSlices.length > 0 ? (
          <>
          <div className="h-[340px] w-full relative">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={visibleSlices}
                  cx="50%"
                  cy="50%"
                  innerRadius="55%"
                  outerRadius="80%"
                  startAngle={DONUT_START_ANGLE}
                  endAngle={DONUT_END_ANGLE}
                  paddingAngle={visibleSlices.length > 1 ? 2 : 0}
                  dataKey="value"
                  stroke="none"
                >
                  {visibleSlices.map((slice) => (
                    <Cell key={slice.name} fill={slice.color} />
                  ))}
                </Pie>
                <Tooltip content={<SliceTooltip asCurrency={isValue} />} />
              </PieChart>
            </ResponsiveContainer>
            <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
              <span className="text-sm text-gray-400">Reserva</span>
              <span className="text-lg font-semibold text-white">
                {formatSlice(isValue ? draft.value : draft.percentage)}
              </span>
            </div>
          </div>
          <ChartLegend
            className="mt-3"
            items={visibleSlices.map(slice => ({ name: slice.name, color: slice.color, value: formatSlice(slice.value) }))}
          />
          </>
        ) : (
          <div className="h-[400px] flex items-center justify-center text-gray-500">
            <p>Defina o valor ideal da reserva para visualizar o gráfico</p>
          </div>
        )}
      </Card>
    </div>
  );
};
