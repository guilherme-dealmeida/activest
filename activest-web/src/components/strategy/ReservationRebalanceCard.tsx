import type React from 'react';
import { clsx } from 'clsx';
import { Card } from '@/components/ui/Card';
import { ReservationRebalance } from '@/types';
import { formatCurrency, formatPercentage } from '@/utils/format';
import { RESERVATION_TYPE_LABELS } from '@/utils/reservation';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { STRATEGY_HELP } from './strategyHelp';
import { REBALANCE_TABLE_CLASS, RebalanceColGroup } from './rebalanceColumns';

const signColor = (n: number) => (n > 0 ? 'text-success' : n < 0 ? 'text-danger' : 'text-gray-400');

/**
 * "Rebalanceamento Reserva de Emergência": uma linha, a reserva inteira.
 *
 * Aportar vem do backend. Por porcentagem ele já considera que o aporte
 * entra no total da carteira, então depois dele a reserva bate o percentual
 * exato; por valor é só alvo − reserva. Negativo significa reserva acima do
 * alvo, em vermelho: a tela mostra o excesso, não manda vender — e a
 * Sugestão fica AGUARDAR, como na tabela por tipo.
 */
export const ReservationRebalanceCard = ({ reservation, action }: { reservation: ReservationRebalance; action?: React.ReactNode }) => (
  <Card title="Rebalanceamento Reserva de Emergência" titleInfo={STRATEGY_HELP.reservationRebalance} action={action}>
    <div className="overflow-x-auto mt-4">
      <table className={REBALANCE_TABLE_CLASS}>
        <RebalanceColGroup />
        <thead className="text-xs text-gray-400 uppercase bg-surface-light border-b border-surface-light">
          <tr>
            <th className="px-4 py-3"><LabelWithInfo info={STRATEGY_HELP.reservationType}>Tipo de Estratégia</LabelWithInfo></th>
            <th className="px-4 py-3 text-right"><LabelWithInfo info={STRATEGY_HELP.idealPercentage}>% Ideal</LabelWithInfo></th>
            <th className="px-4 py-3 text-right"><LabelWithInfo info={STRATEGY_HELP.contributed}>Aportado</LabelWithInfo></th>
            <th className="px-4 py-3 text-right"><LabelWithInfo info={STRATEGY_HELP.toContribute}>Aportar</LabelWithInfo></th>
            <th className="px-4 py-3 text-right"><LabelWithInfo info={STRATEGY_HELP.strategic}>Estratégico</LabelWithInfo></th>
            <th className="px-4 py-3 text-right"><LabelWithInfo info={STRATEGY_HELP.portfolioPercentage}>% Carteira</LabelWithInfo></th>
            <th className="px-4 py-3 text-right"><LabelWithInfo info={STRATEGY_HELP.diffPercentage}>% Diff</LabelWithInfo></th>
            <th className="px-4 py-3 text-center"><LabelWithInfo info={STRATEGY_HELP.suggestion}>Sugestão</LabelWithInfo></th>
          </tr>
        </thead>
        <tbody>
          <tr className="hover:bg-surface-light/30">
            <td className="px-4 py-3 font-medium text-white">
              {RESERVATION_TYPE_LABELS[reservation.typeReservation]}
            </td>
            <td className="px-4 py-3 text-right">
              {/* Por valor o alvo é em reais: não há % ideal a mostrar. */}
              {reservation.idealPercentage != null ? formatPercentage(reservation.idealPercentage) : '—'}
            </td>
            <td className="px-4 py-3 text-right">{formatCurrency(reservation.currentValue)}</td>
            <td className="px-4 py-3 text-right font-medium">
              <span className={signColor(reservation.amountToRebalance)}>
                {formatCurrency(reservation.amountToRebalance)}
              </span>
            </td>
            <td className="px-4 py-3 text-right font-medium text-white">{formatCurrency(reservation.idealValue)}</td>
            <td className="px-4 py-3 text-right">{formatPercentage(reservation.currentPercentage)}</td>
            <td className="px-4 py-3 text-right">
              <span className={signColor(reservation.diffPercentage)}>{formatPercentage(reservation.diffPercentage)}</span>
            </td>
            <td className="px-4 py-3 text-center">
              <span
                className={clsx(
                  'px-2.5 py-1 rounded-full text-xs font-medium border',
                  reservation.amountToRebalance > 0
                    ? 'bg-success/20 text-success border-success/30'
                    : 'bg-surface-light text-gray-300 border-gray-700'
                )}
              >
                {reservation.amountToRebalance > 0 ? 'APORTAR' : 'AGUARDAR'}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </Card>
);
