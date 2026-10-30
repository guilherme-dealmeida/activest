import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { dividendsApi } from '@/api/dividends';
import { assetsApi } from '@/api/assets';
import { Card } from '@/components/ui/Card';
import { AssetLogo } from '@/components/ui/AssetLogo';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Pagination } from '@/components/ui/Pagination';
import { formatCurrency, formatCurrencyPrecise, formatDate, formatFxRate, formatMoney, formatShares } from '@/utils/format';
import { AssetType, DIVIDEND_TYPE_LABELS, DividendResponse, DividendSummary } from '@/types';
import { CalendarClock, DollarSign, List } from 'lucide-react';
import { clsx } from 'clsx';
import { useSortable } from '@/hooks/useSortable';
import { TABLE_ACTION_CLASS } from '@/components/ui/tableAction';
import { TickerLink } from '@/components/issuer/TickerLink';
import { TYPE_GROUP_LABELS } from '@/components/dividends/dividendLabels';
import { useDividendFilters } from '@/components/dividends/useDividendFilters';
import { DividendFilterBar, DividendFilterNotice } from '@/components/dividends/DividendFilters';
import { DividendEvolutionTab } from '@/components/dividends/DividendEvolutionTab';
import { ZERO_TOTALS, addDividend, dividendNet, totalsByType } from '@/utils/dividendTotals';

// Proventos de ação vindos só do histórico longo da B3 não têm data de
// pagamento: a fonte não traz o campo (docs/PENDENCIAS.md, 3.4).
// A partir de 2022 a data vem do relatório de proventos que a empresa entrega à CVM
// (docs/planos/PLANO_CARGA_INICIAL.md, 5.2); o que ainda falta, do levantamento 2010–2026 (seção 11).
/** Hover da data de pagamento que não veio da própria fonte do evento (V38, V39). */
const PAYMENT_DATE_SOURCE_HINT: Record<string, string> = {
  CVM: 'Data do relatório de proventos que a empresa entregou à CVM.',
  LEVANTAMENTO: 'Data do levantamento de datas de pagamento (fontes públicas), na falta de uma fonte oficial.',
};

/** D16: provento sem data de pagamento, em qualquer situação (antigo ou ainda não anunciado). */
const NO_PAYMENT_DATE_LABEL = 'Data não informada';
const NO_PAYMENT_DATE_HINT =
  'Nenhuma fonte informa a data de pagamento deste provento. Proventos recentes ganham a data quando a empresa a divulgar.';

/** Linha da tabela: o provento com o TOTAL que vale (líquido; bruto se não há regra de IR). */
type DividendRow = DividendResponse & { total: number };

/** "17,5%", "15%", "Isento"; sem regra de IR, nulo. */
const formatRate = (rate: number | null): string | null => {
  if (rate == null) return null;
  if (rate === 0) return 'Isento';
  return `${(rate * 100).toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`;
};


const NO_TAX_RULE_HINT = 'Sem regra de IR para este tipo de provento e de ativo: o total é o bruto.';

/**
 * Aba da tela: Evolução dos Proventos (meses e anos, 27/10/2026 — docs/planos/PLANO_DIVIDENDOS_POR_PERIODO.md),
 * Histórico (recebidos) ou A Receber. A situação de cada provento vem do backend (`received`, no fuso da B3), e cada
 * aba de lista mostra só a sua parte — por isso a tela não tem filtro nem coluna de situação.
 */
type DividendTab = 'periods' | 'received' | 'toReceive';
type ListTab = Exclude<DividendTab, 'periods'>;

const TAB_LABELS: Record<DividendTab, string> = {
  periods: 'Evolução dos Proventos',
  received: 'Histórico de Proventos',
  toReceive: 'Proventos a Receber',
};

/** Valor de `?tab=` de cada aba; a Evolução é a padrão, sem parâmetro (D2). */
const TAB_PARAMS: Record<DividendTab, string | null> = {
  periods: null,
  received: 'received',
  toReceive: 'to-receive',
};

/** "Ver no Histórico" do detalhe do mês: a aba de lista recebe o ticker e aplica o Listar. */
type ListRequest = { tab: ListTab; ticker: string; nonce: number };

/** Textos que mudam de uma aba para a outra; o resto da aba é igual. */
const TAB_TEXTS: Record<ListTab, { total: string; byType: string; emptyByType: string; emptyList: string }> = {
  received: {
    total: 'Total Recebido',
    byType: 'Recebido por Tipo',
    emptyByType: 'Nenhum provento recebido no filtro.',
    emptyList: 'Nenhum provento encontrado.',
  },
  toReceive: {
    total: 'Total a Receber',
    byType: 'A Receber por Tipo',
    emptyByType: 'Nenhum provento a receber no filtro.',
    emptyList: 'Nenhum provento a receber.',
  },
};

/**
 * Proventos são somente leitura: todos vêm da sincronização automática, que
 * calcula o total com as cotas que havia na data-com.
 *
 * Três abas sobre o mesmo resumo (`GET /dividends/`): Evolução dos Proventos (meses e anos, 27/10/2026, a padrão),
 * Histórico de Proventos (recebidos) e Proventos a Receber (11/10/2026). A aba fica na URL (`?tab=received`,
 * `?tab=to-receive`; sem parâmetro, a Evolução): o card Proventos do Dashboard leva direto a cada uma.
 */
export const DividendsPage = () => {
  const [params, setParams] = useSearchParams();
  const tabParam = params.get('tab');
  const activeTab: DividendTab = tabParam === 'to-receive' ? 'toReceive' : tabParam === 'received' ? 'received' : 'periods';
  const setActiveTab = (tab: DividendTab) => {
    const next = new URLSearchParams(params);
    const value = TAB_PARAMS[tab];
    if (value) next.set('tab', value); else next.delete('tab');
    setParams(next, { replace: true });
  };
  const [listRequest, setListRequest] = useState<ListRequest | null>(null);
  /** Detalhe do mês → aba de lista do provento, filtrada pelo ativo. */
  const showInList = (ticker: string, received: boolean) => {
    const tab: ListTab = received ? 'received' : 'toReceive';
    setListRequest({ tab, ticker, nonce: Date.now() });
    setActiveTab(tab);
  };

  const { data: summary, isLoading } = useQuery<DividendSummary>({
    queryKey: ['dividends', 'summary'],
    queryFn: dividendsApi.getSummary
  });

  // TOTAL = líquido (docs/planos/PLANO_IR_PROVENTOS.md); sem regra de IR, o bruto.
  const allRows = useMemo<DividendRow[]>(
    () => (summary?.recent ?? []).map(r => ({ ...r, total: dividendNet(r) })),
    [summary]
  );
  const receivedRows = useMemo(() => allRows.filter(r => r.received), [allRows]);
  const toReceiveRows = useMemo(() => allRows.filter(r => !r.received), [allRows]);

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  const tabs: DividendTab[] = ['periods', 'received', 'toReceive'];

  return (
    <div className="space-y-6">
      {/* Abas no mesmo estilo da Estratégia. */}
      <div className="flex space-x-1 bg-surface p-1 rounded-lg w-fit border border-surface-light">
        {tabs.map(tab => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={clsx(
              'px-4 py-2 text-sm font-medium rounded-md transition-colors',
              activeTab === tab ? 'bg-surface-light text-white' : 'text-gray-400 hover:text-white hover:bg-surface-light/50'
            )}
          >
            {TAB_LABELS[tab]}
          </button>
        ))}
      </div>

      {/* As três abas ficam montadas: cada uma guarda os seus filtros, página e ordenação ao trocar de aba. */}
      <div hidden={activeTab !== 'periods'}>
        <DividendEvolutionTab rows={allRows} onShowInList={showInList} />
      </div>
      <div hidden={activeTab !== 'received'}>
        <DividendsTabPanel tab="received" rows={receivedRows} listRequest={listRequest} />
      </div>
      <div hidden={activeTab !== 'toReceive'}>
        <DividendsTabPanel tab="toReceive" rows={toReceiveRows} listRequest={listRequest} />
      </div>
    </div>
  );
};

/**
 * Conteúdo de uma aba: cards de total e por tipo, filtros e lista.
 *
 * Os filtros rodam no navegador sobre os proventos da aba, e os cards do topo
 * somam a lista filtrada.
 */
const DividendsTabPanel = ({ tab, rows, listRequest }: { tab: ListTab; rows: DividendRow[]; listRequest: ListRequest | null }) => {
  const texts = TAB_TEXTS[tab];
  // Nome técnico do tipo, para o logo pelo par (tipo, ticker): o ticker se repete entre tipos (V13)
  const { data: assetTypes = [] } = useQuery<AssetType[]>({ queryKey: ['assetTypes'], queryFn: assetsApi.getTypes });
  const typeNameOf = (assetTypeId: number | null | undefined) => assetTypes.find(t => t.id === assetTypeId)?.name ?? null;
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const f = useDividendFilters(rows, () => setCurrentPage(1));
  const { filtered, list } = f;

  // "Ver no Histórico" do detalhe do mês (Evolução dos Proventos): o Listar do ativo nesta aba.
  const { listTicker } = f;
  useEffect(() => {
    if (listRequest?.tab === tab) listTicker(listRequest.ticker);
    // só quando chega um pedido novo (nonce); listTicker muda a cada render
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [listRequest?.nonce]);

  // Cards acompanham o filtro e mostram o líquido, com bruto e IR embaixo.
  // contas em utils/dividendTotals.ts (testadas)
  const totals = useMemo(() => filtered.reduce(addDividend, ZERO_TOTALS), [filtered]);
  const byTypeFiltered = useMemo(() => totalsByType(filtered), [filtered]);

  // Padrão pela data de pagamento (12/10/2026): no histórico, o mais recente
  // primeiro; no a receber, o próximo a pagar primeiro. Sem data de pagamento
  // ("Data não informada") vai para o fim nos dois — o useSortable põe nulo por último.
  const { sortedData, requestSort, getSortIndicator, getSortClass } = useSortable(filtered, {
    key: 'paymentDate',
    direction: tab === 'received' ? 'desc' : 'asc',
  });
  const totalPages = Math.ceil(sortedData.length / itemsPerPage);
  const paginated = sortedData.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  return (
    <div className="space-y-6">
      <DividendFilterNotice f={f} />

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card
          title={texts.total}
          value={formatCurrency(totals.net)}
          description={
            <span className="text-xs text-gray-500">
              Bruto {formatCurrency(totals.gross)} · IR {formatCurrency(totals.tax)}
            </span>
          }
          icon={tab === 'received'
            ? <DollarSign className="text-success" />
            : <CalendarClock className="text-gray-300" />}
          className="md:col-span-1"
        />
        <Card className="md:col-span-2">
          <h3 className="text-sm font-medium text-gray-400 mb-4">{texts.byType}</h3>
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
            {byTypeFiltered.length === 0 && <p className="text-sm text-gray-500">{texts.emptyByType}</p>}
          </div>
        </Card>
      </div>

      {/* Filtros: abaixo dos totais (que os acompanham) e acima da tabela. */}
      <DividendFilterBar f={f} />

      <Card>
        <div className="mb-6">
          <h3 className="text-lg font-medium text-white">{TAB_LABELS[tab]}</h3>
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
                <th className="px-4 py-3 text-center">AÇÕES</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {paginated.map((row) => (
                <tr key={row.id} className="hover:bg-white/5 transition-colors">
                  <td className="px-4 py-3 text-white">
                    {/* Igual à carteira: logo à esquerda, nome no hover. null = tipo sem logo. */}
                    <div className="flex items-center gap-3" title={row.assetName || undefined}>
                      {row.hasLogo !== null && row.hasLogo !== undefined && (
                        <AssetLogo ticker={row.ticker} hasLogo={row.hasLogo} assetType={typeNameOf(row.assetTypeId)} size={38} shape="square" />
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
                      : <span className="text-gray-500 cursor-help whitespace-nowrap" title={NO_PAYMENT_DATE_HINT}>{NO_PAYMENT_DATE_LABEL}</span>}
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-gray-300">
                      {DIVIDEND_TYPE_LABELS[row.type] ?? row.type}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    {row.sharesOnDate != null ? formatShares(row.sharesOnDate) : <span className="text-gray-500">—</span>}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <span className="inline-flex flex-col items-end">
                      <span>{formatCurrencyPrecise(row.amountPerShare)}</span>
                      {row.foreign && (
                        <span className="text-xs text-gray-400 tabular-nums"
                          title={`Câmbio ${formatFxRate(row.foreign.fxRate)} (PTAX compra do pagamento)`}>
                          {formatMoney(row.foreign.originalAmountPerShare, row.foreign.symbol, 2, 8)}
                        </span>
                      )}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    {row.foreign
                      ? <span className="cursor-help" title={[row.foreign.withholdingLegalBasis ?? 'Sem retenção no país de origem',
                          `IR brasileiro estimado: ${formatCurrency(row.foreign.incomeTax ?? 0)} (apuração anual pode mudar)`].join(' · ')}>
                          {formatRate(row.foreign.withholdingRate) ?? '0%'} exterior
                        </span>
                      : formatRate(row.withholdingRate) != null
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
                    title={row.foreign
                      ? `Bruto ${formatCurrency(row.totalAmount)} · retido no exterior ${formatCurrency(row.foreign.withholdingTax ?? 0)}`
                        + ` · IR estimado ${formatCurrency(row.foreign.incomeTax ?? 0)}`
                      : row.withholdingTax != null
                      ? `Bruto ${formatCurrency(row.totalAmount)} · IR ${formatCurrency(row.withholdingTax)}`
                      : NO_TAX_RULE_HINT}
                  >
                    {formatCurrency(row.total)}
                  </td>
                  <td className="px-4 py-3 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button
                        type="button"
                        onClick={() => list(row)}
                        disabled={row.assetTypeId == null}
                        className={TABLE_ACTION_CLASS}
                        title="Ver os proventos deste ativo"
                      >
                        <List className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {paginated.length === 0 && (
                <tr><td colSpan={10} className="px-4 py-8 text-center text-gray-500">{texts.emptyList}</td></tr>
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
