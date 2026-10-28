import { useQuery } from '@tanstack/react-query';
import { strategyApi } from '@/api/strategy';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { formatCurrency, formatPercentage } from '@/utils/format';
import { clsx } from 'clsx';
import { Rebalance } from '@/types';
import { ReservationRebalanceCard } from '@/components/strategy/ReservationRebalanceCard';
import { STRATEGY_HELP } from '@/components/strategy/strategyHelp';
import { REBALANCE_TABLE_CLASS, RebalanceColGroup } from '@/components/strategy/rebalanceColumns';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { PageLink } from '@/components/ui/PageLink';
import { rebalanceTotals } from '@/utils/strategyRules';

/**
 * Rebalanceamento (11/10/2026): era a terceira aba de Estratégia de Investimentos e virou tela própria, com item no
 * menu. O conteúdo é o mesmo: a reserva primeiro, depois a geração de renda por tipo. Cada card tem, à direita, o
 * link para a aba da Estratégia correspondente (17/10/2026). A chave da consulta continua
 * ['strategy', 'rebalance'], então salvar o Ideal Estratégico, a Estratégia Reserva ou ligar a reserva na Carteira
 * (todos invalidam ['strategy']) já atualiza esta tela.
 */
export const RebalancePage = () => {
  const { data: rebalance, isLoading } = useQuery<Rebalance>({
    queryKey: ['strategy', 'rebalance'],
    queryFn: strategyApi.getRebalance
  });

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  // Totais: quanto aportar somado e quanto a carteira passa a valer depois desses aportes.
  const { toInvest: totalToInvest, strategic: strategicTotal } = rebalanceTotals(rebalance?.items || []);

  return (
    <div className="space-y-6">
      {/* Desde 17/10/2026 cada card leva à aba da Estratégia que o alimenta (antes um link só, para a tela) */}
      {rebalance?.reservation && (
        <ReservationRebalanceCard
          reservation={rebalance.reservation}
          action={<PageLink to="/strategy?tab=reservation" label="Ir para Estratégia Reserva" />}
        />
      )}
      <Card title="Sugestão de Aporte (Rebalanceamento)" titleInfo={STRATEGY_HELP.typeRebalance}
        action={<PageLink to="/strategy" label="Ir para Ideal Estratégico" />}>
        <div className="overflow-x-auto mt-4">
          <table className={REBALANCE_TABLE_CLASS}>
            <RebalanceColGroup />
            <thead className="text-xs text-gray-400 uppercase bg-surface-light border-b border-surface-light">
              <tr>
                <th className="px-4 py-3">Tipo Ativo</th>
                <th className="px-4 py-3 text-right"><LabelWithInfo info={STRATEGY_HELP.idealPercentage}>% Ideal</LabelWithInfo></th>
                <th className="px-4 py-3 text-right"><LabelWithInfo info={STRATEGY_HELP.contributed}>Aportado</LabelWithInfo></th>
                <th className="px-4 py-3 text-right"><LabelWithInfo info={STRATEGY_HELP.toContribute}>Aportar</LabelWithInfo></th>
                <th className="px-4 py-3 text-right"><LabelWithInfo info={STRATEGY_HELP.strategic}>Estratégico</LabelWithInfo></th>
                <th className="px-4 py-3 text-right"><LabelWithInfo info={STRATEGY_HELP.portfolioPercentage}>% Carteira</LabelWithInfo></th>
                <th className="px-4 py-3 text-right"><LabelWithInfo info={STRATEGY_HELP.diffPercentage}>% Diff</LabelWithInfo></th>
                <th className="px-4 py-3 text-center"><LabelWithInfo info={STRATEGY_HELP.suggestion}>Sugestão</LabelWithInfo></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-light">
              {rebalance?.items.map((item, idx) => {
                // Valor que o tipo passa a ter depois do aporte sugerido:
                // vem do backend, que ja aplica a regra do tipo mais acima do ideal.
                const strategicValue = item.idealValue;
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
                      {formatCurrency(strategicValue)}
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
                  <td className="px-4 py-3 text-right font-semibold text-success">{formatCurrency(totalToInvest)}</td>
                  <td className="px-4 py-3 text-right font-semibold text-white">{formatCurrency(strategicTotal)}</td>
                  <td colSpan={3}></td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      </Card>
    </div>
  );
};
