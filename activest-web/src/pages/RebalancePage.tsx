import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { strategyApi } from '@/api/strategy';
import { assetsApi } from '@/api/assets';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { formatCurrency, formatPercentage } from '@/utils/format';
import { clsx } from 'clsx';
import { AssetType, Rebalance, RebalanceItem } from '@/types';
import { ReservationRebalanceCard } from '@/components/strategy/ReservationRebalanceCard';
import { STRATEGY_HELP } from '@/components/strategy/strategyHelp';
import { REBALANCE_TABLE_CLASS, RebalanceColGroup } from '@/components/strategy/rebalanceColumns';
import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { PageLink } from '@/components/ui/PageLink';
import { AssetTypeName } from '@/components/ui/AssetTypeName';
import {
  REBALANCE_SUGGESTION_RANK, RebalanceSuggestion, rebalanceSuggestion, rebalanceTotals, relativeDeviation,
} from '@/utils/strategyRules';
import { useSortable } from '@/hooks/useSortable';

/** Linha da tabela: o item do backend + as colunas calculadas na tela, para a ordenação usar as mesmas chaves. */
type RebalanceRow = RebalanceItem & {
  relativeDeviation: number | null;
  suggestion: RebalanceSuggestion;
  /** Chave de ordenação da Sugestão: por prioridade, não alfabética. */
  suggestionRank: number;
  /** Nome interno do tipo (ACAO, ETF...), para o ícone; a linha da API traz só a descrição. */
  assetTypeKey: string | null;
  /** Link da aba do tipo na Carteira; null quando o tipo não tem posição (não há aba para abrir). */
  portfolioLink: string | null;
};

const SUGGESTION_CLASS: Record<RebalanceSuggestion, string> = {
  APORTAR: 'bg-success/20 text-success border-success/30',
  COMPLEMENTAR: 'bg-accent/20 text-accent border-accent/30',
  AGUARDAR: 'bg-surface-light text-gray-300 border-gray-700',
};

const signColor = (n: number) => (n > 0 ? 'text-success' : n < 0 ? 'text-danger' : 'text-gray-400');

/**
 * Rebalanceamento (11/10/2026): era a terceira aba de Estratégia de Investimentos e virou tela própria, com item no
 * menu. O conteúdo é o mesmo: a reserva primeiro, depois a geração de renda por tipo. Cada card tem, à direita, o
 * link para a aba da Estratégia correspondente (17/10/2026). A chave da consulta continua
 * ['strategy', 'rebalance'], então salvar o Ideal Estratégico, a Estratégia Reserva ou ligar a reserva na Carteira
 * (todos invalidam ['strategy']) já atualiza esta tela.
 *
 * 09/11/2026: coluna Desvio Relativo (% Diff ÷ % Ideal, calculada na tela) e colunas ordenáveis pelo cabeçalho
 * (asc → desc → ordem do backend, como no Preço Teto). A linha Total fica fixa no rodapé. Padrão: Desvio Relativo
 * crescente, do mais abaixo do ideal à referência. Clicar na linha abre a aba do tipo na Carteira (`?tab=`); a linha
 * chega com a description do tipo, e o nome sai de ['assets', 'types'] (a mesma consulta da Carteira).
 */
export const RebalancePage = () => {
  const { data: rebalance, isLoading } = useQuery<Rebalance>({
    queryKey: ['strategy', 'rebalance'],
    queryFn: strategyApi.getRebalance
  });

  const navigate = useNavigate();
  const { data: assetTypes } = useQuery<AssetType[]>({ queryKey: ['assets', 'types'], queryFn: assetsApi.getTypes });

  const rows = useMemo<RebalanceRow[]>(() => {
    const nameByDescription = new Map((assetTypes ?? []).map(t => [t.description, t.name]));
    return (rebalance?.items || []).map(item => {
      const relDev = relativeDeviation(item.diffPercentage, item.idealPercentage);
      const suggestion = rebalanceSuggestion(item.amountToRebalance, relDev);
      const typeName = nameByDescription.get(item.typeName);
      return {
        ...item,
        relativeDeviation: relDev,
        suggestion,
        suggestionRank: REBALANCE_SUGGESTION_RANK[suggestion],
        assetTypeKey: typeName ?? null,
        portfolioLink: typeName && item.currentValue > 0 ? `/portfolio?tab=${typeName.toLowerCase()}` : null,
      };
    });
  }, [rebalance, assetTypes]);
  const { sortedData, requestSort, getSortIndicator, getSortClass } = useSortable(rows, { key: 'relativeDeviation', direction: 'asc' });

  const th = (key: keyof RebalanceRow, label: string, info?: string, align = 'text-right') => (
    <th className={clsx('px-4 py-3 cursor-pointer select-none', align, getSortClass(key))} onClick={() => requestSort(key)}>
      {label}
      {info && (
        // o (i) não ordena a coluna
        <span className="inline-flex align-middle ml-1.5 normal-case" onClick={e => e.stopPropagation()}>
          <InfoTooltip content={info} />
        </span>
      )}
      {getSortIndicator(key)}
    </th>
  );

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  // Totais: quanto aportar somado e quanto a carteira passa a valer depois desses aportes.
  const { toInvest: totalToInvest, strategic: strategicTotal } = rebalanceTotals(rebalance?.items || []);

  return (
    <div className="space-y-6">
      <Card title="Sugestão de Aporte (Rebalanceamento)" titleInfo={STRATEGY_HELP.typeRebalance}
        action={<PageLink to="/strategy" label="Ir para Ideal Estratégico" />}>
        <div className="overflow-x-auto mt-4">
          <table className={REBALANCE_TABLE_CLASS}>
            <RebalanceColGroup />
            <thead className="text-xs text-gray-400 uppercase bg-surface-light border-b border-surface-light">
              <tr>
                {th('typeName', 'Tipo Ativo', undefined, 'text-left')}
                {th('idealPercentage', '% Ideal', STRATEGY_HELP.idealPercentage)}
                {th('currentValue', 'Aportado', STRATEGY_HELP.contributed)}
                {th('amountToRebalance', 'Aportar', STRATEGY_HELP.toContribute)}
                {th('idealValue', 'Estratégico', STRATEGY_HELP.strategic)}
                {th('currentPercentage', '% Carteira', STRATEGY_HELP.portfolioPercentage)}
                {th('diffPercentage', '% Diff', STRATEGY_HELP.diffPercentage)}
                {th('relativeDeviation', 'Desvio Relativo', STRATEGY_HELP.relativeDeviation)}
                {th('suggestionRank', 'Sugestão', STRATEGY_HELP.typeSuggestion, 'text-center')}
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-light">
              {sortedData.map(item => {
                // Valor que o tipo passa a ter depois do aporte sugerido:
                // vem do backend, que ja aplica a regra do tipo mais acima do ideal.
                const strategicValue = item.idealValue;
                return (
                  <tr key={item.typeName}
                    className={clsx('hover:bg-surface-light/30', item.portfolioLink && 'cursor-pointer')}
                    title={item.portfolioLink ? `Ver ${item.typeName} na Carteira` : undefined}
                    onClick={item.portfolioLink ? () => navigate(item.portfolioLink as string) : undefined}>
                    <td className="px-4 py-3 font-medium text-white"><AssetTypeName name={item.assetTypeKey} label={item.typeName} /></td>
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
                    <td className="px-4 py-3 text-right">
                      {item.relativeDeviation != null
                        ? <span className={signColor(item.relativeDeviation)}>{formatPercentage(item.relativeDeviation)}</span>
                        : '—'}
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span className={clsx(
                        "px-2.5 py-1 rounded-full text-xs font-medium border",
                        SUGGESTION_CLASS[item.suggestion]
                      )}>
                        {item.suggestion}
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
                  <td colSpan={4}></td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      </Card>
      {/* Desde 17/10/2026 cada card leva à aba da Estratégia que o alimenta (antes um link só, para a tela) */}
      {rebalance?.reservation && (
        <ReservationRebalanceCard
          reservation={rebalance.reservation}
          action={<PageLink to="/strategy?tab=reservation" label="Ir para Estratégia Reserva" />}
        />
      )}
    </div>
  );
};
