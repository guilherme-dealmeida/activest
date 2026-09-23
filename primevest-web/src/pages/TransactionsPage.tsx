import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2, Pencil, X } from 'lucide-react';
import { transactionsApi } from '@/api/transactions';
import { assetsApi } from '@/api/assets';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Pagination } from '@/components/ui/Pagination';
import { TransactionFormModal } from '@/components/transactions/TransactionFormModal';
import { formatCurrency, formatDate, formatShares, toLocalISODate } from '@/utils/format';
import { TransactionResponse, TransactionType, AssetType } from '@/types';
import { useSortable } from '@/hooks/useSortable';
import { isFixedIncomeNote } from '@/utils/assetTypeRules';
import { apiMessage } from '@/utils/apiError';
import { useToast } from '@/context/ToastContext';

/** Rótulo do tipo como a listagem o mostra — a mesma convenção dos DTOs. */
const labelOfType = (type: AssetType) => type.description || type.name;

const ordenarPt = (a: string, b: string) => a.localeCompare(b, 'pt-BR');

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
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [filterType, setFilterType] = useState('ALL');
  const [filterCategory, setFilterCategory] = useState('ALL');
  const [filterAsset, setFilterAsset] = useState('ALL');
  const [filterPeriod, setFilterPeriod] = useState('ALL');

  /**
   * Filtro vindo da carteira, pelo ícone Listar.
   *
   * Filtra por POSIÇÃO, não por ticker: a carteira repete ticker desde que a
   * posição passou a ser por título, e três CDBs são três linhas iguais na
   * coluna do ativo.
   */
  const positionFilter = searchParams.get('position');

  // Mesma queryKey do AssetTypeSelect, entao nao ha requisicao extra: o cache
  // do React Query e um so. A pagina precisa da lista para reconstruir o tipo
  // na edicao, a partir do assetTypeId que a transacao carrega.
  const { data: assetTypes } = useQuery<AssetType[]>({
    queryKey: ['assetTypes'],
    queryFn: assetsApi.getTypes as any
  });

  const { data: transactions, isLoading } = useQuery<TransactionResponse[]>({
    queryKey: ['transactions'],
    queryFn: transactionsApi.list as any
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

  const posicaoFiltrada = positionFilter
    ? enriched.find(row => row.portfolioAssetId === positionFilter)
    : undefined;

  const filtered = enriched.filter(row => {
    if (positionFilter && row.portfolioAssetId !== positionFilter) return false;
    if (filterType !== 'ALL' && row.assetType !== filterType) return false;
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
    setSearchParams(params, { replace: true });
    setCurrentPage(1);
  };

  const handleOpenModal = () => {
    setEditing(null);
    setIsModalOpen(true);
  };

  const handleEdit = (row: TransactionResponse) => {
    setEditing(row);
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
    const pergunta = partes > 1
      ? `Esta venda de ${row.ticker} está dividida em ${partes} títulos. Excluir a venda inteira?`
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
      {positionFilter && (
        <div className="flex items-center gap-3 rounded-md border border-accent/30 bg-accent/10 px-4 py-2 text-sm">
          <span className="text-gray-200">
            Mostrando apenas as transações de{' '}
            <span className="font-medium text-white">{posicaoFiltrada?.ticker ?? 'uma posição da carteira'}</span>
            {posicaoFiltrada?.issuer ? ` · ${posicaoFiltrada.issuer}` : ''}
            {posicaoFiltrada?.maturityDate ? ` · venc. ${formatDate(posicaoFiltrada.maturityDate)}` : ''}
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
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('transactionDate')}`} onClick={() => requestSort('transactionDate')}>
                  DATA TRANSAÇÃO{getSortIndicator('transactionDate')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('assetType')}`} onClick={() => requestSort('assetType')}>
                  TIPO ATIVO{getSortIndicator('assetType')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('ticker')}`} onClick={() => requestSort('ticker')}>
                  ATIVO{getSortIndicator('ticker')}
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
                  <td className="px-4 py-3">{formatDate(row.transactionDate)}</td>
                  <td className="px-4 py-3 text-gray-400">{row.assetType}</td>
                  <td className="px-4 py-3 text-white font-medium">
                    {row.ticker}
                    {/* A venda do Tesouro sai dos títulos mais antigos primeiro, e
                        pode consumir mais de um: cada parte é uma linha, e a marca
                        diz que elas são a mesma venda. */}
                    {(row.groupParts ?? 0) > 1 && (
                      <span className="ml-2 rounded bg-white/10 px-1.5 py-0.5 text-[10px] font-normal text-gray-300">
                        título {row.groupPart}/{row.groupParts}
                      </span>
                    )}
                    {row.isFullSettlement && (
                      <span className="ml-2 rounded bg-danger/15 px-1.5 py-0.5 text-[10px] font-normal text-danger">
                        liquidação total
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span className={row.type === TransactionType.BUY ? "text-success font-medium" : "text-danger font-medium"}>
                      {row.type === TransactionType.BUY ? 'Compra' : 'Venda'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">{row.shares != null ? formatShares(row.shares) : '—'}</td>
                  <td className="px-4 py-3 text-right">{row.pricePerShare != null ? formatCurrency(row.pricePerShare) : '—'}</td>
                  <td className="px-4 py-3 text-right">{formatCurrency(row.brokerageFee)}</td>
                  <td className="px-4 py-3 text-right font-medium text-white">{formatCurrency(row.totalAmount)}</td>
                  <td className="px-4 py-3 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button
                        onClick={() => handleEdit(row)}
                        className="text-gray-500 hover:text-accent transition-colors p-1"
                        title={(row.groupParts ?? 0) > 1 ? 'Editar a venda inteira' : 'Editar'}
                      >
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleDelete(row)}
                        className="text-gray-500 hover:text-danger transition-colors p-1"
                        title={(row.groupParts ?? 0) > 1 ? 'Excluir a venda inteira' : 'Excluir'}
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {paginated.length === 0 && (
                <tr><td colSpan={9} className="px-4 py-8 text-center text-gray-500">Nenhuma transação encontrada.</td></tr>
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
        onClose={() => { setIsModalOpen(false); setEditing(null); }}
        editing={editing}
        assetTypes={assetTypes}
      />
    </div>
  );
};
