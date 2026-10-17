import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { portfolioApi } from '@/api/portfolio';
import { performanceApi } from '@/api/performance';
import { dividendsApi } from '@/api/dividends';
import { Card } from '@/components/ui/Card';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { formatCurrency, formatPercentage, getReturnColor } from '@/utils/format';
import { Wallet, TrendingUp, PiggyBank, DollarSign } from 'lucide-react';
import { AllocationPieChart } from '@/components/charts/AllocationPieChart';
import { CumulativeCard } from '@/components/performance/CumulativeCard';
import { PerformanceSummary } from '@/types/performance';
import { POSITIONS_TWR_KEY, classicReturn } from '@/utils/returns';

/** Uma linha de card do Dashboard: rótulo e valor (link quando leva a outra tela). */
interface StatLine {
  label: string;
  value: React.ReactNode;
  to?: string;
  className?: string;
  /** Rótulo em cima e valor embaixo, à esquerda (Aportes e Rentabilidade, 17/10/2026); sem isto, numa linha só. */
  stacked?: boolean;
}

/**
 * Card do Dashboard em linhas (17/10/2026): a primeira em destaque (rótulo em cima, valor grande), as demais numa
 * linha só, rótulo à esquerda e valor à direita. Substitui o valor único com
 * descrição que os cards tinham até então.
 */
const StatCard = ({ title, titleInfo, icon, lines }: {
  title: string;
  titleInfo?: React.ReactNode;
  icon: React.ReactNode;
  lines: StatLine[];
}) => (
  <Card title={title} titleInfo={titleInfo} icon={icon}>
    <div className="-mt-2 space-y-2">
      {lines.map((line, i) => {
        const value = line.to
          ? <Link to={line.to} className="hover:underline underline-offset-2">{line.value}</Link>
          : line.value;
        // A primeira em destaque (rótulo em cima, valor grande); as demais numa linha só, rótulo e valor.
        if (i === 0) {
          return (
            <div key={line.label}>
              <span className="block text-xs text-gray-400">{line.label}</span>
              <span className={`block text-2xl font-semibold whitespace-nowrap ${line.className ?? 'text-white'}`}>{value}</span>
            </div>
          );
        }
        if (line.stacked) {
          return (
            <div key={line.label}>
              <span className="block text-xs text-gray-400">{line.label}</span>
              <span className={`block text-sm font-medium whitespace-nowrap ${line.className ?? 'text-white'}`}>{value}</span>
            </div>
          );
        }
        return (
          // Em card estreito o valor desce para a linha de baixo, alinhado à direita, em vez de vazar do card.
          <div key={line.label} className="flex flex-wrap items-baseline justify-between gap-x-3">
            <span className="text-xs text-gray-400 whitespace-nowrap">{line.label}</span>
            <span className={`ml-auto text-sm font-medium text-right whitespace-nowrap ${line.className ?? 'text-white'}`}>{value}</span>
          </div>
        );
      })}
    </div>
  </Card>
);

/** TWR do período com o resultado em reais, ou o motivo de não haver número. */
const returnLine = (label: string, perf: PerformanceSummary | undefined, to: string, stacked = false): StatLine => {
  if (!perf) return { label, stacked, value: '—', className: 'text-gray-500' };
  if (perf.twr == null) {
    return {
      label,
      stacked,
      value: !perf.coverage.available ? 'indisponível' : '—',
      className: 'text-gray-500',
    };
  }
  return {
    label,
    stacked,
    to,
    className: getReturnColor(perf.twr),
    value: <>
      {formatPercentage(perf.twr)}
      {perf.result != null && <span className={`ml-2 text-sm font-normal ${getReturnColor(perf.result)}`}>{formatCurrency(perf.result)}</span>}
    </>,
  };
};

export const DashboardPage = () => {
  const { data: summary, isLoading: loadingSummary } = useQuery({
    queryKey: ['portfolio', 'summary'],
    queryFn: portfolioApi.getSummary
  });

  const { data: allocation, isLoading: loadingAllocation } = useQuery({
    queryKey: ['portfolio', 'allocation'],
    // Carteira inteira, reserva incluída. Embrulhado numa arrow porque o
    // React Query passa o próprio contexto como primeiro argumento, e ele
    // chegaria em getAllocation como excludeReservation.
    queryFn: () => portfolioApi.getAllocation()
  });

  const { data: assets, isLoading: loadingAssets } = useQuery({
    queryKey: ['portfolio', 'assets'],
    queryFn: portfolioApi.getAssets
  });

  // Rentabilidade pelo mesmo motor da tela Rentabilidade (docs/planos/PLANO_RENTABILIDADE.md, L13). Tudo em base
  // BRUTA no Dashboard (17/10/2026): cards, Top 5 e o gráfico acumulado comparam igual com os índices, que são brutos.
  const { data: performance } = useQuery({
    queryKey: ['performance', 'dashboard', 'max', 'bruto'],
    queryFn: () => performanceApi.summary({ range: 'MAX', basis: 'GROSS' }),
  });
  const { data: performance12 } = useQuery({
    queryKey: ['performance', 'dashboard', '12m', 'bruto'],
    queryFn: () => performanceApi.summary({ range: '1A', basis: 'GROSS' }),
  });
  const { data: byAsset } = useQuery({
    queryKey: POSITIONS_TWR_KEY,
    queryFn: () => performanceApi.breakdown({ range: 'MAX', basis: 'GROSS' }, 'POSITION'),
  });
  const twrByPosition = new Map((byAsset?.rows ?? []).map(r => [r.positionId, r.individual]));

  // Proventos: o mesmo resumo da tela Dividendos (líquido recebido, últimos 12 meses e a receber)
  const { data: dividends } = useQuery({
    queryKey: ['dividends', 'summary'],
    queryFn: dividendsApi.getSummary,
  });

  // Rentabilidade acumulada (anexo 1 do pedido de 17/10/2026): recorte fixo, sem filtros na tela — carteira total com
  // reserva, bruto, 12 meses, comparada ao CDI.
  const { data: cumulative } = useQuery({
    queryKey: ['performance', 'dashboard', 'cumulative'],
    queryFn: () => performanceApi.series({ reservation: 'WITH', basis: 'GROSS', range: '1A', benchmarks: 'CDI' }),
  });

  if (loadingSummary || loadingAllocation || loadingAssets) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner /></div>;
  }

  const topAssets = assets ? [...assets].sort((a, b) => b.currentValue - a.currentValue).slice(0, 5) : [];

  const chartData = (allocation || []).map(item => ({
    name: item.typeName,
    value: item.value,
    percentage: item.percentage,
    color: item.colorHex
  }));

  return (
    <div className="space-y-6">
      {/* Cards (17/10/2026): Patrimônio, Aportes, Rentabilidade e Proventos, cada um em linhas */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
        <StatCard
          title="Patrimônio"
          titleInfo="Valor atual (bruto) das posições em aberto — cotação, PU do Tesouro, curva da renda fixa —; o valor líquido, com o IR e o IOF estimados de um resgate hoje na renda fixa e no Tesouro (nos demais tipos é o próprio valor atual); e quanto foi investido nelas."
          icon={<Wallet className="h-5 w-5" />}
          lines={[
            { label: 'Valor atual', value: formatCurrency(summary?.currentValue || 0) },
            { label: 'Valor líquido', value: formatCurrency(summary?.currentNetValue || 0) },
            { label: 'Valor investido', value: formatCurrency(summary?.totalInvested || 0) },
          ]}
        />
        <StatCard
          title="Aportes"
          titleInfo="Aportes líquidos dos últimos 12 meses: as compras menos o que as vendas devolveram (o líquido na renda fixa e no Tesouro). Recomprar com o dinheiro de uma venda não conta como aporte novo. Embaixo, a média por mês (o total dividido por 12)."
          icon={<PiggyBank className="h-5 w-5" />}
          lines={[
            { label: 'Últimos 12 meses', value: formatCurrency(summary?.contributions12m || 0) },
            { label: 'Média mensal', value: formatCurrency(summary?.contributionsMonthlyAverage12m || 0), stacked: true },
          ]}
        />
        <StatCard
          title="Rentabilidade"
          titleInfo="Rentabilidade bruta da carteira ponderada pelo tempo (TWR): aportes e retiradas não contam como rendimento. Ao lado, o resultado em reais (valorização + proventos). Detalhes e comparação com os índices na tela Rentabilidade."
          icon={<TrendingUp className="h-5 w-5" />}
          lines={[
            returnLine('Últimos 12 meses', performance12, '/rentabilidade?periodo=1A'),
            returnLine('Desde o início', performance, '/rentabilidade?periodo=MAX', true),
          ]}
        />
        <StatCard
          title="Proventos"
          titleInfo="Proventos líquidos (bruto onde não há IR): recebidos nos últimos 12 meses, recebidos desde o início e os anunciados com pagamento futuro."
          icon={<DollarSign className="h-5 w-5" />}
          lines={[
            { label: 'Últimos 12 meses', value: formatCurrency(dividends?.last12Months || 0), to: '/dividends' },
            { label: 'Totais', value: formatCurrency(dividends?.totalDividends || 0), to: '/dividends' },
            { label: 'A receber', value: formatCurrency(dividends?.totalToReceive || 0), to: '/dividends?aba=a-receber' },
          ]}
        />
      </div>

      {/* Charts & Tables */}
      <div className="grid grid-cols-1 xl:grid-cols-5 gap-6">
        <div className="xl:col-span-2 bg-surface rounded-lg border border-white/10 p-6">
          <h3 className="text-lg font-semibold text-white mb-4">Alocação da Carteira</h3>
          {/* Sem altura fixa: rosca centralizada e legenda no canto inferior direito (CornerLegendDonut, 12/10/2026). */}
          <div>
            <AllocationPieChart 
              data={chartData} 
              totalValue={summary?.currentValue || 0}
              legend="corner"
            />
          </div>
        </div>
        
        <div className="xl:col-span-3 bg-surface rounded-lg border border-white/10 p-6">
          <h3 className="text-lg font-semibold text-white mb-4">Top 5 Ativos</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-white/10">
                  <th className="text-left py-3 px-4 text-gray-400 font-medium">Ativo</th>
                  <th className="text-left py-3 px-4 text-gray-400 font-medium">Tipo</th>
                  <th className="text-right py-3 px-4 text-gray-400 font-medium">Valor Atual</th>
                  <th className="text-right py-3 px-4 text-gray-400 font-medium">
                    <LabelWithInfo info="Rentabilidade com os proventos: (valorização + proventos) ÷ valor aplicado, a mesma da Carteira. Embaixo, o TWR bruto desde a primeira compra, a rentabilidade no tempo da tela Rentabilidade.">
                      Rentabilidade
                    </LabelWithInfo>
                  </th>
                </tr>
              </thead>
              <tbody>
                {topAssets.map((asset) => (
                  <tr key={asset.id} className="border-b border-white/5 hover:bg-white/5 transition-colors">
                    <td className="py-3 px-4">
                      <div>
                        <span className="text-white font-medium">{asset.ticker}</span>
                        <p className="text-gray-500 text-xs">{asset.assetName}</p>
                      </div>
                    </td>
                    <td className="py-3 px-4 text-gray-400">{asset.typeName}</td>
                    <td className="py-3 px-4 text-right text-white">{formatCurrency(asset.currentValue)}</td>
                    {/* 17/10/2026: a clássica (como na Carteira) e, embaixo, o TWR — antes só o TWR */}
                    <td className="py-3 px-4 text-right">
                      {(() => {
                        const classic = classicReturn(asset.valorization, asset.totalDividends, asset.totalInvested);
                        const twr = twrByPosition.get(asset.id);
                        return (
                          <span className="inline-flex flex-col items-end">
                            <span className={`font-medium ${classic != null ? getReturnColor(classic) : 'text-gray-500'}`}>
                              {classic != null ? formatPercentage(classic) : '—'}
                            </span>
                            <span className={`text-xs tabular-nums ${twr != null ? getReturnColor(twr) : 'text-gray-500'}`}>
                              TWR: {twr != null ? formatPercentage(twr) : '—'}
                            </span>
                          </span>
                        );
                      })()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Rentabilidade acumulada: só o gráfico, com o recorte fixo da consulta acima; abaixo de Alocação e Top 5 */}
      {cumulative && !cumulative.coverage.empty && <CumulativeCard data={cumulative} />}
    </div>
  );
};
