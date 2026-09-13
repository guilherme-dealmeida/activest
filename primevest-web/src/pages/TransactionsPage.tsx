import React, { useState, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { transactionsApi } from '@/api/transactions';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Pagination } from '@/components/ui/Pagination';
import { formatCurrency, formatDate, formatShares } from '@/utils/format';
import { TransactionResponse, TransactionCreate, TransactionType } from '@/types';
import { Plus, Trash2, ArrowUpRight, ArrowDownLeft } from 'lucide-react';
import { useSortable } from '@/hooks/useSortable';
import { getAssetType, getAllAssetTypes } from '@/utils/assetTypes';

// Currency mask helper
function toCurrencyDisplay(value: number): string {
  if (!value && value !== 0) return '';
  return value.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function parseCurrency(value: string): number {
  const cleaned = value.replace(/[^\d,]/g, '');
  const normalized = cleaned.replace(',', '.');
  const num = parseFloat(normalized);
  return isNaN(num) ? 0 : num;
}

export const TransactionsPage = () => {
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(15);
  const [filterType, setFilterType] = useState('ALL');
  const [filterDateFrom, setFilterDateFrom] = useState('');
  const [filterDateTo, setFilterDateTo] = useState('');
  const [formData, setFormData] = useState<TransactionCreate>({
    ticker: '',
    type: TransactionType.BUY,
    shares: 0,
    pricePerShare: 0,
    brokerageFee: 0,
    transactionDate: new Date().toISOString().split('T')[0]
  });

  const [priceDisplay, setPriceDisplay] = useState('');
  const [feeDisplay, setFeeDisplay] = useState('');

  const { data: transactions, isLoading } = useQuery<TransactionResponse[]>({
    queryKey: ['transactions'],
    queryFn: transactionsApi.list as any
  });

  const createMutation = useMutation({
    mutationFn: transactionsApi.create as any,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      setIsModalOpen(false);
      resetForm();
    }
  });

  const deleteMutation = useMutation({
    mutationFn: transactionsApi.delete as any,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
    }
  });

  const resetForm = useCallback(() => {
    setFormData({
      ticker: '', type: TransactionType.BUY, shares: 0,
      pricePerShare: 0, brokerageFee: 0,
      transactionDate: new Date().toISOString().split('T')[0]
    });
    setPriceDisplay('');
    setFeeDisplay('');
  }, []);

  const enriched = (transactions || []).map(t => ({ ...t, assetType: getAssetType(t.ticker) }));

  const filtered = enriched.filter(row => {
    if (filterType !== 'ALL' && row.assetType !== filterType) return false;
    if (filterDateFrom && row.transactionDate < filterDateFrom) return false;
    if (filterDateTo && row.transactionDate > filterDateTo) return false;
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
    createMutation.mutate(formData as any);
  };

  const handleOpenModal = () => {
    resetForm();
    setIsModalOpen(true);
  };

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
            {getAllAssetTypes().map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">De</label>
          <input
            type="date"
            value={filterDateFrom}
            onChange={(e) => { setFilterDateFrom(e.target.value); setCurrentPage(1); }}
            className="bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2"
          />
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Até</label>
          <input
            type="date"
            value={filterDateTo}
            onChange={(e) => { setFilterDateTo(e.target.value); setCurrentPage(1); }}
            className="bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2"
          />
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
                <th className="px-4 py-3 text-center">AÇÃO</th>
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
                    <button
                      onClick={() => { if(window.confirm('Tem certeza?')) deleteMutation.mutate(row.id as any); }}
                      className="text-gray-500 hover:text-danger transition-colors p-1"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
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

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title="Nova Transação">
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Toggle Compra/Venda */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">Operação</label>
            <div className="flex bg-surface rounded-lg border border-white/10 p-1">
              <button
                type="button"
                onClick={() => setFormData({...formData, type: TransactionType.BUY})}
                className={`flex-1 flex items-center justify-center gap-2 py-2.5 rounded-md text-sm font-medium transition-all ${
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
                onClick={() => setFormData({...formData, type: TransactionType.SELL})}
                className={`flex-1 flex items-center justify-center gap-2 py-2.5 rounded-md text-sm font-medium transition-all ${
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

          {/* Tipo de Ativo */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-1.5">Tipo de Ativo</label>
            <select
              className="flex h-10 w-full rounded-md border border-surface-light bg-surface px-3 py-2 text-sm text-white focus:ring-2 focus:ring-accent focus:border-transparent"
              defaultValue=""
            >
              <option value="" disabled>Selecione o tipo</option>
              {getAllAssetTypes().map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>

          {/* Ticker */}
          <Input
            label="Ticker (Ativo)"
            value={formData.ticker}
            onChange={(e) => setFormData({...formData, ticker: e.target.value.toUpperCase()})}
            required
            placeholder="Ex: PETR4, HGBS11"
          />

          {/* Quantidade + Preço */}
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Quantidade"
              type="number"
              step="any"
              min="0"
              value={formData.shares || ''}
              onChange={(e) => setFormData({...formData, shares: Number(e.target.value)})}
              required
            />
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1.5">Preço Unitário (R$)</label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">R$</span>
                <input
                  type="text"
                  inputMode="decimal"
                  className="flex h-10 w-full rounded-md border border-surface-light bg-surface pl-9 pr-3 py-2 text-sm text-white focus:ring-2 focus:ring-accent focus:border-transparent"
                  value={priceDisplay}
                  onChange={(e) => {
                    const raw = e.target.value.replace(/[^\d,\.]/g, '');
                    setPriceDisplay(raw);
                    setFormData({...formData, pricePerShare: parseCurrency(raw)});
                  }}
                  onBlur={() => {
                    if (formData.pricePerShare > 0) {
                      setPriceDisplay(toCurrencyDisplay(formData.pricePerShare));
                    }
                  }}
                  placeholder="0,00"
                  required
                />
              </div>
            </div>
          </div>

          {/* Taxas + Data */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1.5">Taxas (R$)</label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">R$</span>
                <input
                  type="text"
                  inputMode="decimal"
                  className="flex h-10 w-full rounded-md border border-surface-light bg-surface pl-9 pr-3 py-2 text-sm text-white focus:ring-2 focus:ring-accent focus:border-transparent"
                  value={feeDisplay}
                  onChange={(e) => {
                    const raw = e.target.value.replace(/[^\d,\.]/g, '');
                    setFeeDisplay(raw);
                    setFormData({...formData, brokerageFee: parseCurrency(raw)});
                  }}
                  onBlur={() => {
                    if (formData.brokerageFee && formData.brokerageFee > 0) {
                      setFeeDisplay(toCurrencyDisplay(formData.brokerageFee));
                    }
                  }}
                  placeholder="0,00"
                />
              </div>
            </div>
            <Input
              label="Data"
              type="date"
              value={formData.transactionDate}
              onChange={(e) => setFormData({...formData, transactionDate: e.target.value})}
              required
            />
          </div>

          <div className="pt-4 flex justify-end gap-3 border-t border-surface-light">
            <Button type="button" variant="ghost" onClick={() => setIsModalOpen(false)}>Cancelar</Button>
            <Button type="submit" isLoading={createMutation.isPending}>Salvar</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
