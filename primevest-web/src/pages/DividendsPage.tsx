import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { dividendsApi } from '@/api/dividends';
import { Card } from '@/components/ui/Card';
import { AssetLogo } from '@/components/ui/AssetLogo';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Pagination } from '@/components/ui/Pagination';
import { formatCurrency, formatCurrencyPrecise, formatDate, formatShares } from '@/utils/format';
import { DIVIDEND_TYPE_LABELS, DividendResponse, DividendSummary, DividendType } from '@/types';
import { CalendarClock, DollarSign } from 'lucide-react';
import { useSortable } from '@/hooks/useSortable';
import { Badge } from '@/components/ui/Badge';
import { TickerLink } from '@/components/issuer/TickerLink';

/**
 * Rótulo de cada tipo no filtro e no card "Por Tipo" (plural: é um grupo).
 * A ordem das opções é a do enum; só aparecem os tipos que existem no histórico.
 */
const TYPE_GROUP_LABELS: Record<DividendType, string> = {
  [DividendType.DIVIDENDO]: 'Dividendos',
  [DividendType.JCP]: 'JCP',
  [DividendType.RENDIMENTO]: 'Rendimentos',
  [DividendType.AMORTIZACAO]: 'Amortizações',
  [DividendType.RESTITUICAO_CAPITAL]: 'Restituições de capital',
};
const TYPE_ORDER = Object.values(DividendType);

// Proventos de ação vindos só do histórico longo da B3 não têm data de
// pagamento: a fonte não traz o campo (PENDENCIAS.txt, 3.4).
// A partir de 2022 a data vem do relatório de proventos que a empresa entrega à CVM
// (PLANO_CARGA_INICIAL.md, 5.2).
/** Hover da data de pagamento que não veio da própria fonte do evento (V38). */
const PAYMENT_DATE_SOURCE_HINT: Record<string, string> = {
  CVM: 'Data do relatório de proventos que a empresa entregou à CVM.',
};

const NO_PAYMENT_DATE_HINT =
  'A B3 não informa a data de pagamento deste provento: o histórico longo de proventos de ações não traz esse campo.';

const ALL = 'ALL';

/** Linha da tabela: o provento com o TOTAL que vale (líquido; bruto se não há regra de IR). */
type DividendRow = DividendResponse & { total: number };

/** "17,5%", "15%", "Isento"; sem regra de IR, nulo. */
const formatRate = (rate: number | null): string | null => {
  if (rate == null) return null;
  if (rate === 0) return 'Isento';
  return `${(rate * 100).toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`;
};

/** Filtro de situação: recebido (pagamento até hoje) ou a receber. */
const RECEIVED = 'RECEIVED';
const TO_RECEIVE = 'TO_RECEIVE';

type Totals = { net: number; gross: number; tax: number };
const ZERO: Totals = { net: 0, gross: 0, tax: 0 };
const addRow = (acc: Totals, r: DividendRow): Totals => ({
  net: acc.net + r.total,
  gross: acc.gross + (r.totalAmount ?? 0),
  tax: acc.tax + (r.withholdingTax ?? 0),
});

const NO_TAX_RULE_HINT = 'Sem regra de IR para este tipo de provento e de ativo: o total é o bruto.';
const ordenarPt = (a: string, b: string) => a.localeCompare(b, 'pt-BR');

/**
 * Proventos são somente leitura: todos vêm da sincronização automática, que
 * calcula o total com as cotas que havia na data-com.
 *
 * Os filtros rodam no navegador sobre o histórico inteiro, e os cards do topo
 * somam a lista filtrada.
 */
export const DividendsPage = () => {
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [filterType, setFilterType] = useState<string>(ALL);
  const [filterAssetType, setFilterAssetType] = useState<string>(ALL);
  const [filterAsset, setFilterAsset] = useState<string>(ALL);
  const [filterStatus, setFilterStatus] = useState<string>(ALL);

  const { data: summary, isLoading } = useQuery<DividendSummary>({
    queryKey: ['dividends', 'summary'],
    queryFn: dividendsApi.getSummary
  });

  // TOTAL = líquido (PLANO_IR_PROVENTOS.md); sem regra de IR, o bruto.
  const allRows = useMemo<DividendRow[]>(
    () => (summary?.recent ?? []).map(r => ({ ...r, total: r.netAmount ?? r.totalAmount })),
    [summary]
  );

  // Opções de cada filtro: só o que existe no histórico.
  const typeOptions = useMemo(() => {
    const present = new Set(allRows.map(r => r.type));
    return TYPE_ORDER.filter(t => present.has(t));
  }, [allRows]);

  const assetTypeOptions = useMemo(() => {
    const byId = new Map<number, string>();
    allRows.forEach(r => {
      if (r.assetTypeId != null && !byId.has(r.assetTypeId)) {
        byId.set(r.assetTypeId, r.assetTypeDescription ?? String(r.assetTypeId));
      }
    });
    return [...byId.entries()].sort((a, b) => a[0] - b[0]).map(([id, label]) => ({ id: String(id), label }));
  }, [allRows]);

  // O filtro de ativo depende do tipo de ativo: sem tipo escolhido fica em "Todos".
  const assetOptions = useMemo(() => {
    if (filterAssetType === ALL) return [];
    const tickers = allRows
      .filter(r => String(r.assetTypeId) === filterAssetType)
      .map(r => r.ticker)
      .filter(Boolean);
    return [...new Set(tickers)].sort(ordenarPt);
  }, [allRows, filterAssetType]);

  const filtered = useMemo(
    () =>
      allRows.filter(r => {
        if (filterType !== ALL && r.type !== filterType) return false;
        if (filterAssetType !== ALL && String(r.assetTypeId) !== filterAssetType) return false;
        if (filterAsset !== ALL && r.ticker !== filterAsset) return false;
        if (filterStatus === RECEIVED && !r.received) return false;
        if (filterStatus === TO_RECEIVE && r.received) return false;
        return true;
      }),
    [allRows, filterType, filterAssetType, filterAsset, filterStatus]
  );

  // Cards acompanham o filtro e mostram o líquido, com bruto e IR embaixo.
  // "Recebido" e "a receber" vêm do backend (received), no fuso da B3.
  const receivedTotals = useMemo(() => filtered.filter(r => r.received).reduce(addRow, ZERO), [filtered]);
  const toReceiveTotals = useMemo(() => filtered.filter(r => !r.received).reduce(addRow, ZERO), [filtered]);
  const byTypeFiltered = useMemo(() => {
    const sums = new Map<DividendType, Totals>();
    filtered.filter(r => r.received).forEach(r => sums.set(r.type, addRow(sums.get(r.type) ?? ZERO, r)));
    return TYPE_ORDER.filter(t => sums.has(t)).map(t => ({ type: t, ...(sums.get(t) as Totals) }));
  }, [filtered]);

  const { sortedData, requestSort, getSortIndicator, getSortClass } = useSortable(filtered, {
    key: 'exDate',
    direction: 'desc',
  });
  const totalPages = Math.ceil(sortedData.length / itemsPerPage);
  const paginated = sortedData.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  const handleTypeChange = (value: string) => {
    setFilterType(value);
    setCurrentPage(1);
  };

  // Trocar o tipo de ativo zera o ativo: um ticker de outro tipo deixaria a
  // tabela vazia sem motivo.
  const handleAssetTypeChange = (value: string) => {
    setFilterAssetType(value);
    setFilterAsset(ALL);
    setCurrentPage(1);
  };

  const handleAssetChange = (value: string) => {
    setFilterAsset(value);
    setCurrentPage(1);
  };

  const handleStatusChange = (value: string) => {
    setFilterStatus(value);
    setCurrentPage(1);
  };

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  const selectClass =
    'bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2 disabled:opacity-60 disabled:cursor-not-allowed';

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <Card
          title="Total Recebido"
          value={formatCurrency(receivedTotals.net)}
          description={
            <span className="text-xs text-gray-500">
              Bruto {formatCurrency(receivedTotals.gross)} · IR {formatCurrency(receivedTotals.tax)}
            </span>
          }
          icon={<DollarSign className="text-success" />}
          className="md:col-span-1"
        />
        <Card className="md:col-span-2">
          <h3 className="text-sm font-medium text-gray-400 mb-4">Recebido por Tipo</h3>
          <div className="flex flex-wrap gap-8">
            {byTypeFiltered.map(({ type, net, gross, tax }) => (
              <div key={type}>
                <p className="text-sm text-gray-500">{TYPE_GROUP_LABELS[type]}</p>
                <p className="text-xl font-semibold text-white">{formatCurrency(net)}</p>
                <p className="text-xs text-gray-500">
                  Bruto {formatCurrency(gross)} · IR {formatCurrency(tax)}
                </p>
              </div>
            ))}
            {byTypeFiltered.length === 0 && <p className="text-sm text-gray-500">Nenhum provento recebido no filtro.</p>}
          </div>
        </Card>
        {/* A receber: pagamento depois de hoje. Fica fora da Carteira e do Dashboard. */}
        <Card
          title="Total a Receber"
          value={formatCurrency(toReceiveTotals.net)}
          description={
            <span className="text-xs text-gray-500">
              Bruto {formatCurrency(toReceiveTotals.gross)} · IR {formatCurrency(toReceiveTotals.tax)}
            </span>
          }
          icon={<CalendarClock className="text-gray-300" />}
          className="md:col-span-1"
        />
      </div>

      {/* Filtros: abaixo dos totais (que os acompanham) e acima da tabela. */}
      <div className="flex flex-wrap gap-3 items-end">
        <div>
          <label className="block text-xs text-gray-400 mb-1">Tipo de Ativo</label>
          <select value={filterAssetType} onChange={(e) => handleAssetTypeChange(e.target.value)} className={selectClass}>
            <option value={ALL}>Todos</option>
            {assetTypeOptions.map(o => <option key={o.id} value={o.id}>{o.label}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Ativo</label>
          <select
            value={filterAsset}
            disabled={filterAssetType === ALL}
            onChange={(e) => handleAssetChange(e.target.value)}
            className={selectClass}
          >
            <option value={ALL}>Todos</option>
            {assetOptions.map(a => <option key={a} value={a}>{a}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Tipo</label>
          <select value={filterType} onChange={(e) => handleTypeChange(e.target.value)} className={selectClass}>
            <option value={ALL}>Todos</option>
            {typeOptions.map(t => <option key={t} value={t}>{TYPE_GROUP_LABELS[t]}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Situação</label>
          <select value={filterStatus} onChange={(e) => handleStatusChange(e.target.value)} className={selectClass}>
            <option value={ALL}>Todos</option>
            <option value={RECEIVED}>Recebidos</option>
            <option value={TO_RECEIVE}>A receber</option>
          </select>
        </div>
      </div>

      <Card>
        <div className="mb-6">
          <h3 className="text-lg font-medium text-white">Histórico de Proventos</h3>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-gray-400 uppercase border-b border-white/10">
              <tr>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('ticker')}`} onClick={() => requestSort('ticker')}>
                  ATIVO{getSortIndicator('ticker')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('exDate')}`} onClick={() => requestSort('exDate')}>
                  DATA COM{getSortIndicator('exDate')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('paymentDate')}`} onClick={() => requestSort('paymentDate')}>
                  PAGAMENTO{getSortIndicator('paymentDate')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('type')}`} onClick={() => requestSort('type')}>
                  TIPO{getSortIndicator('type')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('sharesOnDate')}`} onClick={() => requestSort('sharesOnDate')}>
                  COTAS ELEGÍVEIS{getSortIndicator('sharesOnDate')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('amountPerShare')}`} onClick={() => requestSort('amountPerShare')}>
                  VALOR BRUTO COTA{getSortIndicator('amountPerShare')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('withholdingRate')}`} onClick={() => requestSort('withholdingRate')}>
                  ALÍQUOTA{getSortIndicator('withholdingRate')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('netAmountPerShare')}`} onClick={() => requestSort('netAmountPerShare')}>
                  VALOR LÍQUIDO COTA{getSortIndicator('netAmountPerShare')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('total')}`} onClick={() => requestSort('total')}>
                  TOTAL{getSortIndicator('total')}
                </th>
                <th className={`px-4 py-3 text-center cursor-pointer ${getSortClass('received')}`} onClick={() => requestSort('received')}>
                  SITUAÇÃO{getSortIndicator('received')}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {paginated.map((row) => (
                <tr key={row.id} className="hover:bg-white/5 transition-colors">
                  <td className="px-4 py-3 text-white">
                    {/* Igual à carteira: logo à esquerda, nome no hover. null = tipo sem logo. */}
                    <div className="flex items-center gap-3" title={row.assetName || undefined}>
                      {row.hasLogo !== null && row.hasLogo !== undefined && (
                        <AssetLogo ticker={row.ticker} hasLogo={row.hasLogo} size={38} shape="square" />
                      )}
                      <TickerLink ticker={row.ticker} issuerId={row.issuerId} />
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    {formatDate(row.exDate)}
                  </td>
                  <td className="px-4 py-3">
                    {row.paymentDate
                      ? (row.paymentDateSource && PAYMENT_DATE_SOURCE_HINT[row.paymentDateSource]
                          ? <span className="cursor-help underline decoration-dotted decoration-gray-500" title={PAYMENT_DATE_SOURCE_HINT[row.paymentDateSource]}>{formatDate(row.paymentDate)}</span>
                          : formatDate(row.paymentDate))
                      : <span className="text-gray-500 cursor-help" title={NO_PAYMENT_DATE_HINT}>—</span>}
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-gray-300">
                      {DIVIDEND_TYPE_LABELS[row.type] ?? row.type}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    {row.sharesOnDate != null ? formatShares(row.sharesOnDate) : <span className="text-gray-500">—</span>}
                  </td>
                  <td className="px-4 py-3 text-right">{formatCurrencyPrecise(row.amountPerShare)}</td>
                  <td className="px-4 py-3 text-right">
                    {formatRate(row.withholdingRate) != null
                      ? <span className="cursor-help" title={row.withholdingLegalBasis ?? undefined}>{formatRate(row.withholdingRate)}</span>
                      : <span className="text-gray-500 cursor-help" title={NO_TAX_RULE_HINT}>—</span>}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {row.netAmountPerShare != null
                      ? formatCurrencyPrecise(row.netAmountPerShare)
                      : <span className="text-gray-500 cursor-help" title={NO_TAX_RULE_HINT}>—</span>}
                  </td>
                  <td
                    className="px-4 py-3 text-right text-success font-medium cursor-help"
                    title={row.withholdingTax != null
                      ? `Bruto ${formatCurrency(row.totalAmount)} · IR ${formatCurrency(row.withholdingTax)}`
                      : NO_TAX_RULE_HINT}
                  >
                    {formatCurrency(row.total)}
                  </td>
                  {/* Mesmo selo da SUGESTÃO da Estratégia: verde recebido, cinza a receber. */}
                  <td className="px-4 py-3 text-center">
                    {row.received
                      ? <Badge variant="success" className="py-1">RECEBIDO</Badge>
                      : <Badge variant="default" className="py-1">A RECEBER</Badge>}
                  </td>
                </tr>
              ))}
              {paginated.length === 0 && (
                <tr><td colSpan={10} className="px-4 py-8 text-center text-gray-500">Nenhum provento encontrado.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={setCurrentPage}
          itemsPerPage={itemsPerPage}
          onItemsPerPageChange={(size) => { setItemsPerPage(size); setCurrentPage(1); }}
          totalItems={sortedData.length}
        />
      </Card>
    </div>
  );
};
