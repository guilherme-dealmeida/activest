import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Edit2, RefreshCw, Trash2 } from 'lucide-react';
import { ceilingPricesApi } from '@/api/ceilingPrices';
import { Card } from '@/components/ui/Card';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Badge } from '@/components/ui/Badge';
import { AssetLogo } from '@/components/ui/AssetLogo';
import { TickerLink } from '@/components/issuer/TickerLink';
import { IssuerSearch } from '@/components/issuer/IssuerSearch';
import { useSortable } from '@/hooks/useSortable';
import { formatCurrency, formatDate } from '@/utils/format';
import { CeilingPriceListItem, CeilingStatus } from '@/types';
import { IDEAL_PRICE_INFO, STATUS_LABEL, STATUS_VARIANT, pct } from '@/components/ceilingprice/ceilingFormat';
import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { MarginPair } from '@/components/ceilingprice/MarginPair';
import { TABLE_ACTION_CLASS } from '@/components/ui/tableAction';

/**
 * Preço Teto — lista dos cálculos do usuário (docs/planos/PLANO_PRECO_TETO.md, 7.1). Cada linha compara o teto e o
 * ideal com três preços (D13): o do cálculo, o fechamento de hoje e o preço médio da carteira.
 */
export const CeilingPricesPage = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<CeilingStatus | 'ALL'>('ALL');

  const { data, isLoading } = useQuery<CeilingPriceListItem[]>({
    queryKey: ['ceilingPrices'],
    queryFn: ceilingPricesApi.list,
  });

  const remove = useMutation({
    mutationFn: ceilingPricesApi.delete,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ceilingPrices'] }),
  });

  const rows = (data ?? []).filter(r => filter === 'ALL' || r.status === filter);
  const { sortedData, requestSort, getSortIndicator, getSortClass } = useSortable(rows);

  const th = (key: keyof CeilingPriceListItem, label: string, align = 'text-right', info?: string) => (
    <th className={`px-3 py-3 cursor-pointer whitespace-nowrap ${align} ${getSortClass(key as string)}`}
        onClick={() => requestSort(key as string)}>
      {label}
      {info && (
        // o (i) não ordena a coluna
        <span className="inline-flex align-middle ml-1 normal-case" onClick={e => e.stopPropagation()}>
          <InfoTooltip content={info} />
        </span>
      )}
      {getSortIndicator(key as string)}
    </th>
  );

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex items-end gap-3">
          <div>
            <label className="block text-xs text-gray-400 mb-1">Situação</label>
            <select
              value={filter}
              onChange={e => setFilter(e.target.value as CeilingStatus | 'ALL')}
              className="bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2"
            >
              <option value="ALL">Todas</option>
              {(Object.keys(STATUS_LABEL) as CeilingStatus[]).map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
            </select>
          </div>
        </div>
        <div className="w-full max-w-sm">
          <label className="block text-xs text-gray-400 mb-1">Novo cálculo</label>
          <IssuerSearch
            placeholder="Ação para calcular (ex: VALE3, PETR4)"
            addLabel="Calcular preço teto da ação"
            onSelect={ticker => navigate(`/ceiling-prices/${encodeURIComponent(ticker)}`)}
          />
        </div>
      </div>

      <Card className="p-0">
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-gray-400 uppercase border-b border-white/10">
              <tr>
                {th('ticker', 'Ativo', 'text-left')}
                {th('ceilingPrice', 'Preço teto')}
                {th('idealPrice', 'Preço ideal', 'text-right', IDEAL_PRICE_INFO)}
                {th('calcCeilingMarginPct', 'Preço do cálculo')}
                {th('todayCeilingMarginPct', 'Preço atual')}
                {th('averageCeilingMarginPct', 'Preço médio')}
                {th('status', 'Situação', 'text-center')}
                {th('calculatedAt', 'Calculado em')}
                <th className="px-3 py-3 text-right">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {sortedData.map(row => (
                <tr key={row.id} className="hover:bg-white/5 transition-colors cursor-pointer"
                    onClick={() => navigate(`/ceiling-prices/${encodeURIComponent(row.ticker)}`)}>
                  <td className="px-3 py-3">
                    <div className="flex items-center gap-2">
                      <AssetLogo ticker={row.ticker} hasLogo={!!row.hasLogo} />
                      <div>
                        <TickerLink ticker={row.ticker} issuerId={row.issuerId} className="font-medium text-white" />
                        <div className="text-xs text-gray-500 truncate max-w-[180px]">{row.assetName}</div>
                      </div>
                    </div>
                  </td>
                  <td className="px-3 py-3 text-right font-semibold text-accent tabular-nums">
                    {row.ceilingPrice == null ? '—' : formatCurrency(row.ceilingPrice)}
                  </td>
                  <td className="px-3 py-3 text-right">
                    <div className="tabular-nums">{row.idealPrice == null ? '—' : formatCurrency(row.idealPrice)}</div>
                    <div className="text-xs text-gray-500 tabular-nums whitespace-nowrap">MS: {pct(row.safetyMarginPct, 1)}</div>
                  </td>
                  <td className="px-3 py-3 text-right">
                    <div className="tabular-nums">{formatCurrency(row.calcPrice)}</div>
                    <MarginPair ceiling={row.calcCeilingMarginPct} ideal={row.calcIdealMarginPct} />
                  </td>
                  <td className="px-3 py-3 text-right">
                    <div className="tabular-nums">{row.todayPrice == null ? '—' : formatCurrency(row.todayPrice)}</div>
                    <MarginPair ceiling={row.todayCeilingMarginPct} ideal={row.todayIdealMarginPct} />
                  </td>
                  <td className="px-3 py-3 text-right">
                    {row.averagePrice == null ? <span className="text-gray-600">—</span> : (
                      <>
                        <div className="tabular-nums">{formatCurrency(row.averagePrice)}</div>
                        <MarginPair ceiling={row.averageCeilingMarginPct} ideal={row.averageIdealMarginPct} />
                      </>
                    )}
                  </td>
                  <td className="px-3 py-3 text-center">
                    <Badge variant={STATUS_VARIANT[row.status]}>{STATUS_LABEL[row.status]}</Badge>
                  </td>
                  <td className="px-3 py-3 text-right text-gray-400 whitespace-nowrap">
                    {formatDate(row.calculatedAt.slice(0, 10))}
                  </td>
                  <td className="px-3 py-3 text-right">
                    <div className="flex justify-end items-center gap-2">
                      {row.defaultsChanged && (
                        <span title="Há padrões mais novos (balanço, Selic, preço): abra para revisar" className="text-accent">
                          <RefreshCw className="w-4 h-4" />
                        </span>
                      )}
                      {(row.blockingCount + row.warningCount) > 0 && (
                        <span title={`${row.blockingCount + row.warningCount} alerta(s) no cálculo`}
                              className={row.blockingCount > 0 ? 'text-danger' : 'text-yellow-500'}>
                          <AlertTriangle className="w-4 h-4" />
                        </span>
                      )}
                      <button title="Abrir / editar" className={TABLE_ACTION_CLASS}
                              onClick={e => { e.stopPropagation(); navigate(`/ceiling-prices/${encodeURIComponent(row.ticker)}`); }}>
                        <Edit2 className="w-4 h-4" />
                      </button>
                      <button title="Excluir" className={TABLE_ACTION_CLASS}
                              onClick={e => {
                                e.stopPropagation();
                                if (window.confirm(`Excluir o cálculo de ${row.ticker}?`)) remove.mutate(row.id);
                              }}>
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {sortedData.length === 0 && (
                <tr>
                  <td colSpan={9} className="px-4 py-10 text-center text-gray-500">
                    Nenhum cálculo de preço teto. Busque uma ação em "Novo cálculo".
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {/* legenda */}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-400">
        <span>Legenda:</span>
        <span><strong className="text-white">MS</strong> = margem de segurança usada no cálculo do preço teto</span>
        <span><strong className="text-white">T</strong> = margem do preço da coluna em relação ao preço teto</span>
        <span><strong className="text-white">I</strong> = margem do preço da coluna em relação ao preço ideal</span>
      </div>
    </div>
  );
};

