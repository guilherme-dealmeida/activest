import React, { useState, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { transactionsApi } from '@/api/transactions';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { CurrencyInput } from '@/components/ui/CurrencyInput';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Pagination } from '@/components/ui/Pagination';
import { formatCurrency, formatDate, formatShares } from '@/utils/format';
import { TransactionResponse, TransactionCreate, TransactionType, Asset, AssetType } from '@/types';
import { Plus, Trash2, Pencil, ArrowUpRight, ArrowDownLeft } from 'lucide-react';
import { useSortable } from '@/hooks/useSortable';
import { AssetTypeSelect } from '@/components/forms/AssetTypeSelect';
import { TickerAutocomplete } from '@/components/forms/TickerAutocomplete';
import { FixedIncomeFields, FixedIncomeValues } from '@/components/forms/FixedIncomeFields';

/**
 * Único ponto do front que compara o tipo por nome. No Tesouro Direto a família
 * do papel já está no próprio nome do título, então a categoria seria uma
 * segunda fonte para o mesmo fato. Se surgir outro tipo com a mesma
 * característica, o lugar certo é uma coluna em asset_types — foi assim que
 * price_source e fixed_income tiraram do código as comparações por string.
 */
const TESOURO_DIRETO = 'TESOURO_DIRETO';

/** Liga os botões do rodapé do Modal ao <form>, que fica na área que rola. */
const FORM_ID = 'transaction-form';

const EMPTY_FIXED_INCOME: FixedIncomeValues = {
  assetName: '', assetCategoryId: null, maturityDate: '',
  rateType: null, indexer: null, indexerRate: null
};

function getDateFromDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().split('T')[0];
}

export const TransactionsPage = () => {
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [filterType, setFilterType] = useState('ALL');
  const [filterPeriod, setFilterPeriod] = useState('30');
  // O tipo escolhido comanda o formulário: restringe o autocomplete e decide
  // se o bloco de renda fixa aparece.
  const [selectedType, setSelectedType] = useState<AssetType | null>(null);
  // Ativo já cadastrado que casou com o ticker. Null = ativo novo, campos editáveis.
  const [matchedAsset, setMatchedAsset] = useState<Asset | null>(null);
  const [fixedIncome, setFixedIncome] = useState<FixedIncomeValues>(EMPTY_FIXED_INCOME);
  const [formData, setFormData] = useState<TransactionCreate>({
    ticker: '',
    type: TransactionType.BUY,
    shares: 0,
    pricePerShare: 0,
    brokerageFee: 0,
    transactionDate: new Date().toISOString().split('T')[0]
  });

  const [tickerInput, setTickerInput] = useState('');

  const { data: transactions, isLoading } = useQuery<TransactionResponse[]>({
    queryKey: ['transactions'],
    queryFn: transactionsApi.list as any
  });

  const createMutation = useMutation({
    mutationFn: (txData: TransactionCreate) => transactionsApi.create(txData),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      setIsModalOpen(false);
      resetForm();
    }
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, txData }: { id: string; txData: TransactionCreate }) => transactionsApi.update(id, txData),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      setIsModalOpen(false);
      resetForm();
    }
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => transactionsApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
    }
  });

  const resetForm = useCallback(() => {
    setEditingId(null);
    setSelectedType(null);
    setMatchedAsset(null);
    setFixedIncome(EMPTY_FIXED_INCOME);
    setTickerInput('');
    setFormData({
      ticker: '', type: TransactionType.BUY, shares: 0,
      pricePerShare: 0, brokerageFee: 0,
      transactionDate: new Date().toISOString().split('T')[0]
    });
  }, []);

  /** Trocar o tipo invalida o ticker: a lista do autocomplete é outra. */
  const handleTypeChange = (type: AssetType | null) => {
    setSelectedType(type);
    setMatchedAsset(null);
    setFixedIncome(EMPTY_FIXED_INCOME);
    setTickerInput('');
    setFormData(prev => ({ ...prev, ticker: '' }));
  };

  /**
   * asset != null -> ticker casou com ativo existente: espelha os dados dele e
   * trava os campos. asset == null -> digitação livre, ativo possivelmente novo.
   */
  const handleTickerChange = (ticker: string, asset: Asset | null) => {
    setTickerInput(ticker);
    setFormData(prev => ({ ...prev, ticker }));
    setMatchedAsset(asset);
    if (asset) {
      setFixedIncome({
        assetName: asset.name ?? '',
        assetCategoryId: asset.categoryId ?? null,
        maturityDate: asset.maturityDate ?? '',
        rateType: asset.rateType ?? null,
        indexer: asset.indexer ?? null,
        indexerRate: asset.indexerRate ?? null
      });
    }
  };

  const enriched = (transactions || []).map(t => ({
    ...t,
    assetType: t.assetTypeName || ''
  }));

  const uniqueTypes = [...new Set(enriched.map(t => t.assetType).filter(Boolean))].sort();

  const filtered = enriched.filter(row => {
    if (filterType !== 'ALL' && row.assetType !== filterType) return false;
    if (filterPeriod !== 'ALL') {
      const minDate = getDateFromDaysAgo(Number(filterPeriod));
      if (row.transactionDate < minDate) return false;
    }
    return true;
  });

  const { sortedData, requestSort, getSortIndicator, getSortClass } = useSortable(filtered);
  const totalPages = Math.ceil(sortedData.length / itemsPerPage);
  const paginated = sortedData.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (editingId) {
      updateMutation.mutate({ id: editingId, txData: formData });
      return;
    }
    // Ativo já existe -> backend ignora os campos de ativo; ainda assim só
    // mandamos o que é relevante, para o payload contar a mesma história.
    const payload: TransactionCreate = {
      ...formData,
      assetTypeId: selectedType?.id ?? null,
      ...(matchedAsset ? {} : {
        assetName: fixedIncome.assetName || null,
        assetCategoryId: fixedIncome.assetCategoryId,
        maturityDate: fixedIncome.maturityDate || null,
        ...(selectedType?.fixedIncome ? {
          rateType: fixedIncome.rateType,
          indexer: fixedIncome.indexer,
          indexerRate: fixedIncome.indexerRate
        } : {})
      })
    };
    createMutation.mutate(payload);
  };

  const handleOpenModal = () => {
    resetForm();
    setIsModalOpen(true);
  };

  const handleEdit = (row: TransactionResponse & { assetType: string }) => {
    setEditingId(row.id);
    setTickerInput(row.ticker);
    setFormData({
      ticker: row.ticker,
      type: row.type as TransactionType,
      shares: row.shares,
      pricePerShare: row.pricePerShare,
      brokerageFee: row.brokerageFee || 0,
      transactionDate: row.transactionDate
    });
    setIsModalOpen(true);
  };

  const computedTotal = (formData.shares || 0) * (formData.pricePerShare || 0);
  /**
   * Quando o quadro do título aparece, ele vai para uma COLUNA ao lado dos
   * campos da operação em vez de empilhar acima deles. As duas alturas passam a
   * se sobrepor: o formulário para de crescer o tamanho do quadro inteiro.
   */
  const mostraDadosTitulo =
    !editingId && !!selectedType?.fixedIncome && tickerInput.trim().length >= 2;
  const modalTitle = editingId ? 'Editar Transação' : 'Nova Transação';
  const isSaving = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="space-y-6">
      {/* Filter bar */}
      <div className="flex flex-wrap gap-3 items-end">
        <div>
          <label className="block text-xs text-gray-400 mb-1">Tipo de Ativo</label>
          <select
            value={filterType}
            onChange={(e) => { setFilterType(e.target.value); setCurrentPage(1); }}
            className="bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2"
          >
            <option value="ALL">Todos</option>
            {uniqueTypes.map(t => <option key={t} value={t}>{t}</option>)}
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
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('transactionDate')}`} onClick={() => requestSort('transactionDate')}>
                  DATA{getSortIndicator('transactionDate')}
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
                  <td className="px-4 py-3 text-white font-medium">{row.ticker}</td>
                  <td className="px-4 py-3">
                    <span className={row.type === TransactionType.BUY ? "text-success font-medium" : "text-danger font-medium"}>
                      {row.type === TransactionType.BUY ? 'Compra' : 'Venda'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">{formatShares(row.shares)}</td>
                  <td className="px-4 py-3 text-right">{formatCurrency(row.pricePerShare)}</td>
                  <td className="px-4 py-3 text-right">{formatCurrency(row.brokerageFee)}</td>
                  <td className="px-4 py-3 text-right font-medium text-white">{formatCurrency(row.totalAmount)}</td>
                  <td className="px-4 py-3 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button
                        onClick={() => handleEdit(row)}
                        className="text-gray-500 hover:text-accent transition-colors p-1"
                        title="Editar"
                      >
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => { if(window.confirm('Tem certeza?')) deleteMutation.mutate(row.id); }}
                        className="text-gray-500 hover:text-danger transition-colors p-1"
                        title="Excluir"
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

      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={modalTitle}
        size="xl"
        footer={
          <div className="flex justify-end gap-3">
            <Button type="button" variant="ghost" onClick={() => setIsModalOpen(false)}>Cancelar</Button>
            <Button type="submit" form={FORM_ID} isLoading={isSaving}>Salvar</Button>
          </div>
        }
      >
        {/*
          Duas colunas de alturas independentes: a transação inteira à esquerda,
          o quadro do título à direita. A proporção 3fr/2fr existe porque a
          esquerda ainda se divide em dois campos por linha — repartida meio a
          meio, cada campo ficava com uns 165px e Quantidade/Preço espremiam.
          Sem o quadro, a esquerda ocupa a largura toda.
        */}
        <form
          id={FORM_ID}
          onSubmit={handleSubmit}
          className={`grid grid-cols-1 gap-6 ${mostraDadosTitulo ? 'lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)] lg:items-start' : ''}`}
        >
          {/* Coluna da transação */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 content-start">
            {/* Toggle Compra/Venda */}
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1.5">Operação</label>
              <div className={`flex bg-surface rounded-md border border-white/10 p-1 ${editingId ? 'opacity-60 pointer-events-none' : ''}`}>
                <button
                  type="button"
                  disabled={!!editingId}
                  onClick={() => setFormData({...formData, type: TransactionType.BUY})}
                  className={`flex-1 flex items-center justify-center gap-2 py-1.5 rounded text-sm font-medium transition-all ${
                    formData.type === TransactionType.BUY
                      ? 'bg-success/15 text-success border border-success/30 shadow-sm'
                      : 'text-gray-400 hover:text-gray-300 hover:bg-white/5'
                  }`}
                >
                  <ArrowDownLeft className="w-4 h-4" />
                  Compra
                </button>
                <button
                  type="button"
                  disabled={!!editingId}
                  onClick={() => setFormData({...formData, type: TransactionType.SELL})}
                  className={`flex-1 flex items-center justify-center gap-2 py-1.5 rounded text-sm font-medium transition-all ${
                    formData.type === TransactionType.SELL
                      ? 'bg-danger/15 text-danger border border-danger/30 shadow-sm'
                      : 'text-gray-400 hover:text-gray-300 hover:bg-white/5'
                  }`}
                >
                  <ArrowUpRight className="w-4 h-4" />
                  Venda
                </button>
              </div>
            </div>

            {/* Tipo de Ativo — comanda o resto do formulário */}
            <AssetTypeSelect
              value={selectedType?.id ?? null}
              disabled={!!editingId}
              onChange={handleTypeChange}
            />

            {/* Ticker — autocomplete restrito ao tipo escolhido */}
            <div className="sm:col-span-2">
              {editingId ? (
                <Input label="Ticker (Ativo)" value={tickerInput} readOnly disabled />
              ) : (
                <TickerAutocomplete
                  value={tickerInput}
                  typeId={selectedType?.id ?? null}
                  onChange={handleTickerChange}
                />
              )}
            </div>

            {/* Nome para ativo novo que NÃO é renda fixa */}
            {!editingId && selectedType && !selectedType.fixedIncome && !matchedAsset && tickerInput.trim().length >= 2 && (
              <div className="sm:col-span-2">
                <Input
                  label="Nome do ativo"
                  value={fixedIncome.assetName}
                  onChange={(e) => setFixedIncome(prev => ({ ...prev, assetName: e.target.value }))}
                  placeholder="Ativo novo — informe o nome para cadastrá-lo"
                />
              </div>
            )}

            <Input
              label="Quantidade"
              type="number"
              step="any"
              min="0"
              value={formData.shares || ''}
              onChange={(e) => setFormData({...formData, shares: Number(e.target.value)})}
              required
            />
            <CurrencyInput
              label="Preço Unitário"
              value={formData.pricePerShare}
              onChange={(v) => setFormData({ ...formData, pricePerShare: v })}
              required
            />

            <Input
              label="Data"
              type="date"
              value={formData.transactionDate}
              onChange={(e) => setFormData({...formData, transactionDate: e.target.value})}
              required
            />
            <CurrencyInput
              label="Taxas"
              value={formData.brokerageFee}
              onChange={(v) => setFormData({ ...formData, brokerageFee: v })}
            />

            {/* Total é resultado, não campo: uma linha de leitura ocupa metade
                da altura de um input com rótulo e texto de apoio. */}
            <div className="sm:col-span-2 flex items-baseline justify-between gap-4 rounded-md border border-surface-light bg-surface-light/40 px-4 py-3">
              <span className="text-sm text-gray-400">
                Total <span className="text-gray-500">— quantidade x preço unitário, sem as taxas</span>
              </span>
              <span className="text-lg font-semibold tabular-nums text-white">
                {formatCurrency(computedTotal)}
              </span>
            </div>
          </div>

          {/* Coluna do título — só para tipos com remuneração contratada */}
          {mostraDadosTitulo && selectedType && (
            <FixedIncomeFields
              typeId={selectedType.id}
              values={fixedIncome}
              readOnly={!!matchedAsset}
              showCategory={selectedType.name !== TESOURO_DIRETO}
              onChange={(patch) => setFixedIncome(prev => ({ ...prev, ...patch }))}
            />
          )}
        </form>
      </Modal>
    </div>
  );
};
