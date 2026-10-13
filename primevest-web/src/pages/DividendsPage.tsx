import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { dividendsApi } from '@/api/dividends';
import { Card } from '@/components/ui/Card';
import { AssetLogo } from '@/components/ui/AssetLogo';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Pagination } from '@/components/ui/Pagination';
import { formatCurrency, formatCurrencyPrecise, formatDate, formatShares } from '@/utils/format';
import { DIVIDEND_TYPE_LABELS, DividendResponse, DividendSummary, DividendType } from '@/types';
import { CalendarClock, DollarSign, List, X } from 'lucide-react';
import { clsx } from 'clsx';
import { useSortable } from '@/hooks/useSortable';
import { TABLE_ACTION_CLASS } from '@/components/ui/tableAction';
import { TickerLink } from '@/components/issuer/TickerLink';
import { LocalAssetOption, LocalAssetSearch } from '@/components/ui/LocalAssetSearch';

/**
 * Rótulo de cada tipo no filtro e no card "por Tipo" (plural: é um grupo).
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

const ALL = 'ALL';

/** Linha da tabela: o provento com o TOTAL que vale (líquido; bruto se não há regra de IR). */
type DividendRow = DividendResponse & { total: number };

/** "17,5%", "15%", "Isento"; sem regra de IR, nulo. */
const formatRate = (rate: number | null): string | null => {
  if (rate == null) return null;
  if (rate === 0) return 'Isento';
  return `${(rate * 100).toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`;
};

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
 * Aba da tela: Histórico (recebidos) ou A Receber. A situação de cada provento
 * vem do backend (`received`, no fuso da B3), e cada aba mostra só a sua parte —
 * por isso a tela não tem mais filtro nem coluna de situação.
 */
type DividendTab = 'received' | 'toReceive';

const TAB_LABELS: Record<DividendTab, string> = {
  received: 'Histórico de Proventos',
  toReceive: 'Proventos a Receber',
};

/** Textos que mudam de uma aba para a outra; o resto da aba é igual. */
const TAB_TEXTS: Record<DividendTab, { total: string; byType: string; emptyByType: string; emptyList: string }> = {
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
 * Duas abas sobre o mesmo resumo (`GET /dividends/`): Histórico de Proventos
 * (recebidos) e Proventos a Receber (11/10/2026).
 */
export const DividendsPage = () => {
  const [activeTab, setActiveTab] = useState<DividendTab>('received');

  const { data: summary, isLoading } = useQuery<DividendSummary>({
    queryKey: ['dividends', 'summary'],
    queryFn: dividendsApi.getSummary
  });

  // TOTAL = líquido (docs/planos/PLANO_IR_PROVENTOS.md); sem regra de IR, o bruto.
  const allRows = useMemo<DividendRow[]>(
    () => (summary?.recent ?? []).map(r => ({ ...r, total: r.netAmount ?? r.totalAmount })),
    [summary]
  );
  const receivedRows = useMemo(() => allRows.filter(r => r.received), [allRows]);
  const toReceiveRows = useMemo(() => allRows.filter(r => !r.received), [allRows]);

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  const tabs: DividendTab[] = ['received', 'toReceive'];

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

      {/* As duas abas ficam montadas: cada uma guarda os seus filtros, página e ordenação ao trocar de aba. */}
      <div hidden={activeTab !== 'received'}>
        <DividendsTabPanel tab="received" rows={receivedRows} />
      </div>
      <div hidden={activeTab !== 'toReceive'}>
        <DividendsTabPanel tab="toReceive" rows={toReceiveRows} />
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
const DividendsTabPanel = ({ tab, rows }: { tab: DividendTab; rows: DividendRow[] }) => {
  const texts = TAB_TEXTS[tab];
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [filterType, setFilterType] = useState<string>(ALL);
  const [filterAssetType, setFilterAssetType] = useState<string>(ALL);
  const [filterAsset, setFilterAsset] = useState<string>(ALL);

  // Opções de cada filtro: só o que existe nos proventos da aba.
  const typeOptions = useMemo(() => {
    const present = new Set(rows.map(r => r.type));
    return TYPE_ORDER.filter(t => present.has(t));
  }, [rows]);

  const assetTypeOptions = useMemo(() => {
    const byId = new Map<number, string>();
    rows.forEach(r => {
      if (r.assetTypeId != null && !byId.has(r.assetTypeId)) {
        byId.set(r.assetTypeId, r.assetTypeDescription ?? String(r.assetTypeId));
      }
    });
    return [...byId.entries()].sort((a, b) => a[0] - b[0]).map(([id, label]) => ({ id: String(id), label }));
  }, [rows]);

  // O filtro de ativo depende do tipo de ativo: sem tipo escolhido fica em "Todos".
  const assetOptions = useMemo(() => {
    if (filterAssetType === ALL) return [];
    const tickers = rows
      .filter(r => String(r.assetTypeId) === filterAssetType)
      .map(r => r.ticker)
      .filter(Boolean);
    return [...new Set(tickers)].sort(ordenarPt);
  }, [rows, filterAssetType]);

  const filtered = useMemo(
    () =>
      rows.filter(r => {
        if (filterType !== ALL && r.type !== filterType) return false;
        if (filterAssetType !== ALL && String(r.assetTypeId) !== filterAssetType) return false;
        if (filterAsset !== ALL && r.ticker !== filterAsset) return false;
        return true;
      }),
    [rows, filterType, filterAssetType, filterAsset]
  );

  // Cards acompanham o filtro e mostram o líquido, com bruto e IR embaixo.
  const totals = useMemo(() => filtered.reduce(addRow, ZERO), [filtered]);
  const byTypeFiltered = useMemo(() => {
    const sums = new Map<DividendType, Totals>();
    filtered.forEach(r => sums.set(r.type, addRow(sums.get(r.type) ?? ZERO, r)));
    return TYPE_ORDER.filter(t => sums.has(t)).map(t => ({ type: t, ...(sums.get(t) as Totals) }));
  }, [filtered]);

  // Padrão pela data de pagamento (12/10/2026): no histórico, o mais recente
  // primeiro; no a receber, o próximo a pagar primeiro. Sem data de pagamento
  // ("Data não informada") vai para o fim nos dois — o useSortable põe nulo por último.
  const { sortedData, requestSort, getSortIndicator, getSortClass } = useSortable(filtered, {
    key: 'paymentDate',
    direction: tab === 'received' ? 'desc' : 'asc',
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

  /**
   * Ícone Listar da linha: filtra pelo Tipo de Ativo e pelo Ativo do provento.
   * O filtro Tipo (de provento) volta a "Todos", como os demais filtros no
   * Listar das Transações: a lista mostra todos os proventos daquele ativo.
   */
  const listar = (row: DividendRow) => {
    if (row.assetTypeId == null) return;
    setFilterAssetType(String(row.assetTypeId));
    setFilterAsset(row.ticker);
    setFilterType(ALL);
    setCurrentPage(1);
  };

  // Ativos da aba para a busca por ticker ou nome: um por ticker.
  const searchOptions = useMemo<LocalAssetOption[]>(() => {
    const byTicker = new Map<string, LocalAssetOption>();
    rows.forEach(r => {
      if (r.ticker && r.assetTypeId != null && !byTicker.has(r.ticker)) {
        byTicker.set(r.ticker, { ticker: r.ticker, name: r.assetName ?? '', typeLabel: r.assetTypeDescription ?? '' });
      }
    });
    return [...byTicker.values()];
  }, [rows]);

  /** Escolha na busca: o mesmo efeito do Listar, a partir de um provento daquele ativo. */
  const listarTicker = (ticker: string) => {
    const row = rows.find(r => r.ticker === ticker);
    if (row) listar(row);
  };

  /** "Limpar filtro" do aviso: volta a lista da aba inteira (os três filtros em "Todos"). */
  const limparFiltro = () => {
    setFilterAssetType(ALL);
    setFilterAsset(ALL);
    setFilterType(ALL);
    setCurrentPage(1);
  };

  const selectClass =
    'bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2 disabled:opacity-60 disabled:cursor-not-allowed';

  return (
    <div className="space-y-6">
      {/* Mesmo aviso do Listar das Transações: aparece com um ativo filtrado (pelo Listar, pela busca ou pelo seletor). */}
      {filterAsset !== ALL && (
        <div className="flex items-center gap-3 rounded-md border border-accent/30 bg-accent/10 px-4 py-2 text-sm">
          <span className="text-gray-200">
            Mostrando apenas os proventos de <span className="font-medium text-white">{filterAsset}</span>
          </span>
          <button
            type="button"
            onClick={limparFiltro}
            className="ml-auto flex items-center gap-1 text-xs text-gray-300 hover:text-white transition-colors"
          >
            <X className="w-3.5 h-3.5" />
            Limpar filtro
          </button>
        </div>
      )}

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
      <div className="flex flex-wrap gap-3 items-end">
        {/* Busca por ticker ou nome, como em Emissoras: escolher = Listar. */}
        <div className="w-full sm:w-96">
          <label className="block text-xs text-gray-400 mb-1">Buscar</label>
          <LocalAssetSearch options={searchOptions} onSelect={listarTicker} ariaLabel="Buscar provento por ticker ou nome" />
        </div>
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
      </div>

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
                  <td className="px-4 py-3 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button
                        type="button"
                        onClick={() => listar(row)}
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
