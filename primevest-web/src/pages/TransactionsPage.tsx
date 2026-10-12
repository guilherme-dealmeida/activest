import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2, Pencil, X, RotateCcw, List, ArrowDownLeft, ArrowUpRight } from 'lucide-react';
import { transactionsApi } from '@/api/transactions';
import { assetsApi } from '@/api/assets';
import { portfolioApi } from '@/api/portfolio';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Pagination } from '@/components/ui/Pagination';
import { TransactionFormModal, TransactionPreset } from '@/components/transactions/TransactionFormModal';
import { presetFromPosition, presetFromTransaction } from '@/components/transactions/transactionPreset';
import { TABLE_ACTION_CLASS } from '@/components/ui/tableAction';
import { formatCurrency, formatDate, formatPercentage, formatQuantity, formatUnitPrice, toLocalISODate } from '@/utils/format';
import { TransactionResponse, TransactionType, AssetType, TransactionCreate, PortfolioAssetDetail } from '@/types';
import { useSortable } from '@/hooks/useSortable';
import { isFixedIncomeNote, TESOURO_DIRETO, typeLogo } from '@/utils/assetTypeRules';
import { AssetLogo } from '@/components/ui/AssetLogo';
import { TickerLink } from '@/components/issuer/TickerLink';
import { apiMessage } from '@/utils/apiError';
import { useToast } from '@/context/ToastContext';
import { providerName } from '@/utils/integrations';

/** Rótulo do tipo como a listagem o mostra — a mesma convenção dos DTOs. */
const labelOfType = (type: AssetType) => type.description || type.name;

const ordenarPt = (a: string, b: string) => a.localeCompare(b, 'pt-BR');

/** "12,50% a.a.", "110,00% do CDI", "IPCA + 6,00% a.a." — o mesmo formato da TAXA na carteira. */
const taxaDoTitulo = (t: TransactionResponse): string | null => {
  if (t.indexerRate == null || !t.rateType) return null;
  const rate = formatPercentage(t.indexerRate);
  switch (t.rateType) {
    case 'PREFIXADO':
      return `${rate} a.a.`;
    case 'POS_FIXADO':
      return t.indexer ? `${rate} do ${t.indexer}` : rate;
    case 'HIBRIDO':
      return t.indexer ? `${t.indexer} + ${rate} a.a.` : `${rate} a.a.`;
    default:
      return null;
  }
};

/** Coluna TÍTULO: "Emissor · venc. dd/mm/aaaa · 110,00% do CDI". null fora da renda fixa. */
const rotuloDoTitulo = (t: TransactionResponse): string | null => {
  if (!t.noteId) return null;
  const partes = [
    t.issuer || null,
    t.maturityDate ? `venc. ${formatDate(t.maturityDate)}` : null,
    taxaDoTitulo(t),
  ].filter(Boolean);
  return partes.length ? partes.join(' · ') : null;
};

function getDateFromDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return toLocalISODate(d);
}

export const TransactionsPage = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editing, setEditing] = useState<TransactionResponse | null>(null);
  /** Comprar/Vender pela linha do extrato: o mesmo preset dos ícones da carteira. */
  const [preset, setPreset] = useState<TransactionPreset | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [filterType, setFilterType] = useState('ALL');
  const [filterCategory, setFilterCategory] = useState('ALL');
  const [filterAsset, setFilterAsset] = useState('ALL');
  const [filterPeriod, setFilterPeriod] = useState('ALL');
  const [filterOrigin, setFilterOrigin] = useState<'ALL' | 'MANUAL' | 'AUTO'>('ALL');

  /**
   * Filtro vindo da carteira, pelo ícone Listar.
   *
   * Filtra por POSIÇÃO, não por ticker: a carteira repete ticker desde que a
   * posição passou a ser por título, e três CDBs são três linhas iguais na
   * coluna do ativo.
   */
  const positionFilter = searchParams.get('position');
  /**
   * Filtro por ticker, para o Tesouro Direto depois de salvar: lá cada compra é
   * um título novo (uma posição só dela), e o que interessa é o papel inteiro.
   */
  const tickerFilter = searchParams.get('ticker');

  // Mesma queryKey do AssetTypeSelect, entao nao ha requisicao extra: o cache
  // do React Query e um so. A pagina precisa da lista para reconstruir o tipo
  // na edicao, a partir do assetTypeId que a transacao carrega.
  const { data: assetTypes } = useQuery<AssetType[]>({
    queryKey: ['assetTypes'],
    queryFn: assetsApi.getTypes
  });

  const { data: transactions, isLoading } = useQuery<TransactionResponse[]>({
    queryKey: ['transactions'],
    queryFn: transactionsApi.list
  });

  // As posições da carteira, na mesma queryKey da Carteira (cache único). É
  // delas que Comprar/Vender tiram o preset, para o modal abrir igual nas duas
  // telas — inclusive o saldo na curva do "Resgatar tudo" da Renda Fixa.
  const { data: positions } = useQuery<PortfolioAssetDetail[]>({
    queryKey: ['portfolio', 'assets'],
    queryFn: portfolioApi.getAssets
  });

  // Recebe a linha, e não só o id: o aviso diz o que foi excluído, e numa
  // venda repartida diz que os títulos todos saíram juntos.
  const deleteMutation = useMutation({
    mutationFn: (row: TransactionResponse) => transactionsApi.delete(row.id),
    onSuccess: (_, row) => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      const partes = row.groupParts ?? 1;
      showToast(
        partes > 1
          ? `Venda de ${row.ticker} excluída, com os ${partes} títulos.`
          : `Transação de ${row.ticker} excluída.`,
        'success'
      );
    },
    onError: (e: unknown) => {
      showToast(apiMessage(e) ?? 'Não foi possível excluir a transação.', 'error');
    }
  });

  // Volta a linha editada aos dados que a corretora mandou (regra E4).
  const restoreMutation = useMutation({
    mutationFn: (row: TransactionResponse) => transactionsApi.restore(row.id),
    onSuccess: (_, row) => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      showToast(`Transação de ${row.ticker} voltou aos dados da corretora.`, 'success');
    },
    onError: (e: unknown) => {
      showToast(apiMessage(e) ?? 'Não foi possível restaurar a transação.', 'error');
    }
  });

  const enriched = useMemo(() => (transactions || []).map(t => ({
    ...t,
    assetType: t.assetTypeName || ''
  })), [transactions]);

  const uniqueTypes = [...new Set(enriched.map(t => t.assetType).filter(Boolean))].sort();

  /**
   * O tipo escolhido, como entidade e não como rótulo.
   *
   * O filtro guarda o nome exibido (é o que a listagem mostra na coluna), mas
   * quem decide o segundo filtro é o tipo em si: renda fixa com emissor filtra
   * por ATIVO, porque lá o ativo já é a categoria.
   */
  const tipoSelecionado = useMemo(
    () => (filterType === 'ALL' ? null : (assetTypes || []).find(t => labelOfType(t) === filterType) ?? null),
    [assetTypes, filterType]
  );
  const filtrarPorAtivo = !!tipoSelecionado && isFixedIncomeNote(tipoSelecionado);

  // As transações do tipo escolhido, ignorando os filtros que dependem dele:
  // são elas que dizem quais categorias (ou ativos) têm o que mostrar.
  const doTipo = useMemo(
    () => (filterType === 'ALL' ? [] : enriched.filter(row => row.assetType === filterType)),
    [enriched, filterType]
  );

  // Só as categorias com transação neste tipo. Sem tipo escolhido a lista é
  // vazia e o filtro fica só com "Todas".
  const categoriasDoTipo = useMemo(
    () => [...new Set(doTipo.map(row => row.assetCategoryName).filter(Boolean))].sort(ordenarPt),
    [doTipo]
  );

  const ativosDoTipo = useMemo(
    () => [...new Set(doTipo.map(row => row.ticker).filter(Boolean))].sort(ordenarPt),
    [doTipo]
  );

  // Trocar o tipo zera o filtro dependente: uma categoria de FII não significa
  // nada dentro de Ações, e o filtro preso deixaria a tabela vazia sem motivo.
  const handleTypeChange = (value: string) => {
    setFilterType(value);
    setFilterCategory('ALL');
    setFilterAsset('ALL');
    setCurrentPage(1);
  };

  /** Nome técnico do tipo da linha (a linha traz o id e a descrição). */
  const nomeDoTipo = (row: TransactionResponse | null) =>
    (assetTypes || []).find(t => t.id === row?.assetTypeId)?.name ?? null;

  const posicaoFiltrada = positionFilter
    ? enriched.find(row => row.portfolioAssetId === positionFilter)
    : undefined;

  const filtered = enriched.filter(row => {
    if (positionFilter && row.portfolioAssetId !== positionFilter) return false;
    if (tickerFilter && row.ticker !== tickerFilter) return false;
    if (filterType !== 'ALL' && row.assetType !== filterType) return false;
    if (filterOrigin !== 'ALL' && row.origin !== filterOrigin) return false;
    if (filtrarPorAtivo) {
      if (filterAsset !== 'ALL' && row.ticker !== filterAsset) return false;
    } else if (filterCategory !== 'ALL' && row.assetCategoryName !== filterCategory) {
      return false;
    }
    if (filterPeriod !== 'ALL') {
      const minDate = getDateFromDaysAgo(Number(filterPeriod));
      if (row.transactionDate < minDate) return false;
    }
    return true;
  });

  const { sortedData, requestSort, getSortIndicator, getSortClass } = useSortable(filtered);
  const totalPages = Math.ceil(sortedData.length / itemsPerPage);
  const paginated = sortedData.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  const limparPosicao = () => {
    const params = new URLSearchParams(searchParams);
    params.delete('position');
    params.delete('ticker');
    setSearchParams(params, { replace: true });
    setCurrentPage(1);
  };

  /**
   * Depois de salvar (Nova Transação ou edição), o extrato mostra só o que foi
   * lançado, com o mesmo aviso do ícone Listar da carteira. Renda Fixa e os
   * tipos por cota filtram pela posição — na renda fixa a posição é o título —;
   * o Tesouro filtra pelo ticker, porque cada compra é um título novo.
   *
   * Os outros filtros voltam a "Todos": um "30 dias" ligado esconderia uma
   * compra retroativa, e o usuário acharia que ela não foi gravada.
   */
  const filtrarLancamento = (saved: TransactionResponse | undefined, txData: TransactionCreate) => {
    filtrarPor(saved ?? null, txData.ticker);
  };

  /** Ícone Listar da linha: a mesma regra do filtro depois de salvar. */
  const listar = (row: TransactionResponse) => filtrarPor(row, row.ticker);

  const filtrarPor = (row: TransactionResponse | null, ticker: string) => {
    const params = new URLSearchParams(searchParams);
    params.delete('position');
    params.delete('ticker');
    if (nomeDoTipo(row) === TESOURO_DIRETO || !row?.portfolioAssetId) {
      params.set('ticker', row?.ticker ?? ticker);
    } else {
      params.set('position', row.portfolioAssetId);
    }
    setSearchParams(params, { replace: true });
    setFilterType('ALL');
    setFilterCategory('ALL');
    setFilterAsset('ALL');
    setFilterPeriod('ALL');
    setFilterOrigin('ALL');
    setCurrentPage(1);
  };

  const handleRestore = (row: TransactionResponse) => {
    const corretora = providerName(row.connectionProvider) || 'da corretora';
    if (window.confirm(`Desfazer suas alterações em ${row.ticker} e voltar aos dados de ${corretora}?`)) {
      restoreMutation.mutate(row);
    }
  };

  const handleOpenModal = () => {
    setEditing(null);
    setPreset(null);
    setIsModalOpen(true);
  };

  const handleEdit = (row: TransactionResponse) => {
    setPreset(null);
    setEditing(row);
    setIsModalOpen(true);
  };

  /**
   * Ícones Comprar e Vender da linha, como na carteira.
   *
   * Com a posição aberta, o preset sai dela pela mesma função da Carteira. Com
   * a posição encerrada (não está mais na carteira), sai da própria transação:
   * comprar de novo é legítimo, e uma venda sem saldo a API recusa com a
   * mensagem dela.
   */
  const handleOperate = (row: TransactionResponse, operation: TransactionType) => {
    const type = (assetTypes || []).find(t => t.id === row.assetTypeId);
    if (!type) {
      showToast('Tipo de ativo da transação não encontrado.', 'error');
      return;
    }
    const position = row.portfolioAssetId
      ? positions?.find(p => p.id === row.portfolioAssetId)
      : undefined;
    setEditing(null);
    setPreset(position
      ? presetFromPosition(operation, type, position)
      : presetFromTransaction(operation, type, row));
    setIsModalOpen(true);
  };

  /**
   * Excluir age sobre a VENDA, não sobre a linha.
   *
   * As partes de uma venda repartida entre títulos são a mesma operação do
   * usuário: apagar uma delas deixaria a outra sem sentido, e a API recusaria
   * o pedaço solto de qualquer forma.
   */
  const handleDelete = (row: TransactionResponse & { assetType: string }) => {
    const partes = row.groupParts ?? 1;
    const corretora = providerName(row.connectionProvider) || 'a corretora';
    const pergunta = partes > 1
      ? `Esta venda de ${row.ticker} está dividida em ${partes} títulos. Excluir a venda inteira?`
      : row.origin === 'AUTO'
        ? `Esta transação de ${row.ticker} veio de ${corretora}. Excluída, ela não volta nas próximas sincronizações; `
          + 'dá para restaurar em Integrações › Excluídas. Excluir?'
        : 'Tem certeza?';
    if (window.confirm(pergunta)) {
      deleteMutation.mutate(row);
    }
  };

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  return (
    <div className="space-y-6">
      {(positionFilter || tickerFilter) && (
        <div className="flex items-center gap-3 rounded-md border border-accent/30 bg-accent/10 px-4 py-2 text-sm">
          <span className="text-gray-200">
            Mostrando apenas as transações de{' '}
            {positionFilter ? (
              <>
                <span className="font-medium text-white">{posicaoFiltrada?.ticker ?? 'uma posição da carteira'}</span>
                {posicaoFiltrada?.issuer ? ` · ${posicaoFiltrada.issuer}` : ''}
                {posicaoFiltrada?.maturityDate ? ` · venc. ${formatDate(posicaoFiltrada.maturityDate)}` : ''}
              </>
            ) : (
              <span className="font-medium text-white">{tickerFilter}</span>
            )}
          </span>
          <button
            onClick={limparPosicao}
            className="ml-auto flex items-center gap-1 text-xs text-gray-300 hover:text-white transition-colors"
          >
            <X className="w-3.5 h-3.5" />
            Limpar filtro
          </button>
        </div>
      )}

      {/* Filter bar */}
      <div className="flex flex-wrap gap-3 items-end">
        <div>
          <label className="block text-xs text-gray-400 mb-1">Tipo de Ativo</label>
          <select
            value={filterType}
            onChange={(e) => handleTypeChange(e.target.value)}
            className="bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2"
          >
            <option value="ALL">Todos</option>
            {uniqueTypes.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>

        {/* Filtro dependente do tipo: categoria em quase todos, ativo na renda
            fixa com emissor, onde o ativo (CDB, LCI, LCA) já é a categoria. */}
        {filtrarPorAtivo ? (
          <div>
            <label className="block text-xs text-gray-400 mb-1">Ativo</label>
            <select
              value={filterAsset}
              onChange={(e) => { setFilterAsset(e.target.value); setCurrentPage(1); }}
              className="bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2"
            >
              <option value="ALL">Todos</option>
              {ativosDoTipo.map(a => <option key={a} value={a}>{a}</option>)}
            </select>
          </div>
        ) : (
          <div>
            <label className="block text-xs text-gray-400 mb-1">Categoria</label>
            <select
              value={filterCategory}
              // Sem tipo escolhido só existe "Todas": categoria é do tipo, e
              // misturar as de todos eles não filtraria nada compreensível.
              disabled={filterType === 'ALL' || categoriasDoTipo.length === 0}
              onChange={(e) => { setFilterCategory(e.target.value); setCurrentPage(1); }}
              className="bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2 disabled:opacity-60 disabled:cursor-not-allowed"
            >
              <option value="ALL">Todas</option>
              {categoriasDoTipo.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
        )}

        <div>
          <label className="block text-xs text-gray-400 mb-1">Origem</label>
          <select
            value={filterOrigin}
            onChange={(e) => { setFilterOrigin(e.target.value as 'ALL' | 'MANUAL' | 'AUTO'); setCurrentPage(1); }}
            className="bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2"
          >
            <option value="ALL">Todas</option>
            <option value="MANUAL">Digitadas</option>
            <option value="AUTO">Importadas</option>
          </select>
        </div>

        <div>
          <label className="block text-xs text-gray-400 mb-1">Período</label>
          <select
            value={filterPeriod}
            onChange={(e) => { setFilterPeriod(e.target.value); setCurrentPage(1); }}
            className="bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2"
          >
            <option value="30">30 dias</option>
            <option value="60">60 dias</option>
            <option value="90">90 dias</option>
            <option value="ALL">Todo período</option>
          </select>
        </div>
        <div className="ml-auto">
          <Button onClick={handleOpenModal}>
            <Plus className="w-4 h-4 mr-2" />
            Nova Transação
          </Button>
        </div>
      </div>

      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-gray-400 uppercase border-b border-white/10">
              <tr>
                {/* ATIVO primeiro, como na carteira. A ordem padrão continua a da
                    API: data da transação, da mais nova para a mais antiga. */}
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('ticker')}`} onClick={() => requestSort('ticker')}>
                  ATIVO{getSortIndicator('ticker')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('issuer')}`} onClick={() => requestSort('issuer')}>
                  TÍTULO{getSortIndicator('issuer')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('transactionDate')}`} onClick={() => requestSort('transactionDate')}>
                  DATA TRANSAÇÃO{getSortIndicator('transactionDate')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('assetType')}`} onClick={() => requestSort('assetType')}>
                  TIPO ATIVO{getSortIndicator('assetType')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('type')}`} onClick={() => requestSort('type')}>
                  TIPO{getSortIndicator('type')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('shares')}`} onClick={() => requestSort('shares')}>
                  QUANTIDADE{getSortIndicator('shares')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('pricePerShare')}`} onClick={() => requestSort('pricePerShare')}>
                  PREÇO{getSortIndicator('pricePerShare')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('brokerageFee')}`} onClick={() => requestSort('brokerageFee')}>
                  TAXAS{getSortIndicator('brokerageFee')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('totalAmount')}`} onClick={() => requestSort('totalAmount')}>
                  TOTAL{getSortIndicator('totalAmount')}
                </th>
                <th className="px-4 py-3 text-center">AÇÕES</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {paginated.map((row) => (
                <tr key={row.id} className="hover:bg-white/5 transition-colors">
                  <td className="px-4 py-3 text-white font-medium">
                    {/* Logo e link para a emissora, como na carteira. Nome do ativo no hover. */}
                    <div className="flex items-center gap-3" title={row.assetName || undefined}>
                      {(() => {
                        // Tesouro e Renda Fixa: logo do tipo. Demais: o do ativo (null = tipo sem logo).
                        const logoDoTipo = typeLogo(nomeDoTipo(row));
                        if (logoDoTipo) {
                          return <AssetLogo ticker={row.ticker} hasLogo={!!logoDoTipo.src} src={logoDoTipo.src}
                            label={logoDoTipo.label} size={38} shape="square" />;
                        }
                        return row.hasLogo !== null && row.hasLogo !== undefined
                          ? <AssetLogo ticker={row.ticker} hasLogo={row.hasLogo} size={38} shape="square" />
                          : null;
                      })()}
                      <div className="flex flex-wrap items-center gap-y-1">
                        <TickerLink ticker={row.ticker} issuerId={row.issuerId} />
                        {/* Veio de uma corretora: o selo diz de qual, e se o
                            usuário mexeu depois (a sincronização não sobrescreve). */}
                        {row.origin === 'AUTO' && (
                          <span
                            className="ml-2 rounded bg-accent/15 px-1.5 py-0.5 text-[10px] font-normal text-accent"
                            title={row.editedAt
                              ? `Importada de ${row.connectionLabel ?? providerName(row.connectionProvider)} e editada por você`
                              : `Importada de ${row.connectionLabel ?? providerName(row.connectionProvider)}`}
                          >
                            {providerName(row.connectionProvider) || 'Importada'}{row.editedAt ? ' · editada' : ''}
                          </span>
                        )}
                        {row.isFullSettlement && (
                          <span className="ml-2 rounded bg-danger/15 px-1.5 py-0.5 text-[10px] font-normal text-danger">
                            liquidação total
                          </span>
                        )}
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-white/70">
                    {rotuloDoTitulo(row) ?? '—'}
                    {/* A venda do Tesouro sai dos títulos mais antigos primeiro, e
                        pode consumir mais de um: cada parte é uma linha, e a marca
                        diz que elas são a mesma venda. */}
                    {(row.groupParts ?? 0) > 1 && (
                      <span className="ml-2 rounded bg-white/10 px-1.5 py-0.5 text-[10px] font-normal text-gray-300">
                        título {row.groupPart}/{row.groupParts}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3">{formatDate(row.transactionDate)}</td>
                  <td className="px-4 py-3 text-gray-400">{row.assetType}</td>
                  <td className="px-4 py-3">
                    <span className={row.type === TransactionType.BUY ? "text-success font-medium" : "text-danger font-medium"}>
                      {row.type === TransactionType.BUY ? 'Compra' : 'Venda'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">{row.shares != null ? formatQuantity(row.shares, row.isCrypto) : '—'}</td>
                  <td className="px-4 py-3 text-right">{row.pricePerShare != null ? formatUnitPrice(row.pricePerShare, row.isCrypto) : '—'}</td>
                  <td className="px-4 py-3 text-right">{formatCurrency(row.brokerageFee)}</td>
                  <td className="px-4 py-3 text-right font-medium text-white">{formatCurrency(row.totalAmount)}</td>
                  <td className="px-4 py-3 text-center">
                    <div className="flex items-center justify-center gap-1">
                      {/* Como o Listar da carteira: Tesouro pelo ticker, os demais pela posição. */}
                      <button
                        onClick={() => listar(row)}
                        className={TABLE_ACTION_CLASS}
                        title="Ver as transações desta posição"
                      >
                        <List className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleOperate(row, TransactionType.BUY)}
                        className={TABLE_ACTION_CLASS}
                        title="Comprar"
                      >
                        <ArrowDownLeft className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleOperate(row, TransactionType.SELL)}
                        className={TABLE_ACTION_CLASS}
                        title="Vender"
                      >
                        <ArrowUpRight className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleEdit(row)}
                        className={TABLE_ACTION_CLASS}
                        title={(row.groupParts ?? 0) > 1 ? 'Editar a venda inteira' : 'Editar'}
                      >
                        <Pencil className="w-4 h-4" />
                      </button>
                      {row.origin === 'AUTO' && row.editedAt && (
                        <button
                          onClick={() => handleRestore(row)}
                          disabled={restoreMutation.isPending}
                          className={TABLE_ACTION_CLASS}
                          title="Restaurar dados da corretora"
                          aria-label="Restaurar dados da corretora"
                        >
                          <RotateCcw className="w-4 h-4" />
                        </button>
                      )}
                      <button
                        onClick={() => handleDelete(row)}
                        className={TABLE_ACTION_CLASS}
                        title={(row.groupParts ?? 0) > 1 ? 'Excluir a venda inteira' : 'Excluir'}
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {paginated.length === 0 && (
                <tr><td colSpan={10} className="px-4 py-8 text-center text-gray-500">Nenhuma transação encontrada.</td></tr>
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

      <TransactionFormModal
        isOpen={isModalOpen}
        onClose={() => { setIsModalOpen(false); setEditing(null); setPreset(null); }}
        editing={editing}
        preset={preset}
        assetTypes={assetTypes}
        onSaved={filtrarLancamento}
      />
    </div>
  );
};
