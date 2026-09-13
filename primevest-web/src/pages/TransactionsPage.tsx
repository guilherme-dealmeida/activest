import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { transactionsApi } from '@/api/transactions';
import { assetsApi } from '@/api/assets';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Pagination } from '@/components/ui/Pagination';
import { formatCurrency, formatDate, formatShares } from '@/utils/format';
import { TransactionResponse, TransactionCreate, TransactionType } from '@/types';
import { Plus, Trash2, Pencil, ArrowUpRight, ArrowDownLeft, Search } from 'lucide-react';
import { useSortable } from '@/hooks/useSortable';

interface AssetOption {
  ticker: string;
  name: string;
  typeName: string;
}

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
  const [formAssetType, setFormAssetType] = useState('');
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

  // Autocomplete state
  const [tickerInput, setTickerInput] = useState('');
  const [assetOptions, setAssetOptions] = useState<AssetOption[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  const [highlightIndex, setHighlightIndex] = useState(-1);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Debounced asset search
  useEffect(() => {
    if (tickerInput.length < 2 || editingId) {
      setAssetOptions([]);
      setShowDropdown(false);
      return;
    }
    setIsSearching(true);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      try {
        const results = await assetsApi.getAll(undefined, tickerInput);
        const limited = (results as AssetOption[]).slice(0, 15);
        setAssetOptions(limited);
        setShowDropdown(limited.length > 0);
        setHighlightIndex(-1);
      } catch {
        setAssetOptions([]);
        setShowDropdown(false);
      } finally {
        setIsSearching(false);
      }
    }, 300);
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
  }, [tickerInput, editingId]);

  // Close dropdown on click outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

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
    setFormAssetType('');
    setTickerInput('');
    setAssetOptions([]);
    setShowDropdown(false);
    setFormData({
      ticker: '', type: TransactionType.BUY, shares: 0,
      pricePerShare: 0, brokerageFee: 0,
      transactionDate: new Date().toISOString().split('T')[0]
    });
    setPriceDisplay('');
    setFeeDisplay('');
  }, []);

  const selectAsset = (asset: AssetOption) => {
    setFormData({ ...formData, ticker: asset.ticker });
    setFormAssetType(asset.typeName || '');
    setTickerInput(asset.ticker);
    setShowDropdown(false);
    setAssetOptions([]);
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
    } else {
      createMutation.mutate(formData);
    }
  };

  const handleOpenModal = () => {
    resetForm();
    setIsModalOpen(true);
  };

  const handleEdit = (row: TransactionResponse & { assetType: string }) => {
    setEditingId(row.id);
    setFormAssetType(row.assetType);
    setTickerInput(row.ticker);
    setFormData({
      ticker: row.ticker,
      type: row.type as TransactionType,
      shares: row.shares,
      pricePerShare: row.pricePerShare,
      brokerageFee: row.brokerageFee || 0,
      transactionDate: row.transactionDate
    });
    setPriceDisplay(toCurrencyDisplay(row.pricePerShare));
    setFeeDisplay(row.brokerageFee ? toCurrencyDisplay(row.brokerageFee) : '');
    setIsModalOpen(true);
  };

  const computedTotal = (formData.shares || 0) * (formData.pricePerShare || 0);
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

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={modalTitle}>
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Toggle Compra/Venda */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">Operação</label>
            <div className={`flex bg-surface rounded-lg border border-white/10 p-1 ${editingId ? 'opacity-60 pointer-events-none' : ''}`}>
              <button
                type="button"
                disabled={!!editingId}
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
                disabled={!!editingId}
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

          {/* Tipo de Ativo (auto-preenchido) */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-1.5">Tipo de Ativo</label>
            <input
              type="text"
              readOnly
              className="flex h-10 w-full rounded-md border border-surface-light bg-surface-light/50 px-3 py-2 text-sm text-white cursor-default opacity-70"
              value={formAssetType}
              placeholder="Preenchido ao selecionar ativo"
            />
          </div>

          {/* Ticker - Autocomplete */}
          <div ref={dropdownRef} className="relative">
            <label className="block text-sm font-medium text-gray-300 mb-1.5">Ticker (Ativo)</label>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                ref={inputRef}
                type="text"
                autoComplete="off"
                className={`flex h-10 w-full rounded-md border border-surface-light bg-surface pl-9 pr-3 py-2 text-sm text-white focus:ring-2 focus:ring-accent focus:border-transparent ${editingId ? 'opacity-60 cursor-not-allowed' : ''}`}
                value={tickerInput}
                onChange={(e) => {
                  const val = e.target.value.toUpperCase();
                  setTickerInput(val);
                  setFormData({ ...formData, ticker: val });
                  if (val.length < 2) {
                    setFormAssetType('');
                  }
                }}
                onKeyDown={(e) => {
                  if (!showDropdown || assetOptions.length === 0) return;
                  if (e.key === 'ArrowDown') {
                    e.preventDefault();
                    setHighlightIndex(prev => Math.min(prev + 1, assetOptions.length - 1));
                  } else if (e.key === 'ArrowUp') {
                    e.preventDefault();
                    setHighlightIndex(prev => Math.max(prev - 1, 0));
                  } else if (e.key === 'Enter' && highlightIndex >= 0) {
                    e.preventDefault();
                    selectAsset(assetOptions[highlightIndex]);
                  } else if (e.key === 'Escape') {
                    setShowDropdown(false);
                  }
                }}
                required
                placeholder="Digite o ticker (ex: PETR4, HGBS11)"
                readOnly={!!editingId}
              />
              {isSearching && (
                <LoadingSpinner className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-accent" />
              )}
            </div>
            {showDropdown && assetOptions.length > 0 && !editingId && (
              <div className="absolute z-50 w-full mt-1 max-h-60 overflow-y-auto rounded-md border border-white/10 bg-surface shadow-xl">
                {assetOptions.map((opt, idx) => (
                  <button
                    key={opt.ticker}
                    type="button"
                    onClick={() => selectAsset(opt)}
                    className={`w-full text-left px-3 py-2.5 flex items-center gap-3 text-sm transition-colors border-b border-white/5 last:border-0 ${
                      idx === highlightIndex ? 'bg-accent/15 text-white' : 'text-gray-300 hover:bg-white/5'
                    }`}
                  >
                    <span className="font-semibold text-white min-w-[80px]">{opt.ticker}</span>
                    <span className="truncate flex-1 text-gray-400">{opt.name}</span>
                    <span className="text-xs text-accent/70 whitespace-nowrap">{opt.typeName}</span>
                  </button>
                ))}
              </div>
            )}
            {tickerInput.length >= 2 && !isSearching && assetOptions.length === 0 && showDropdown === false && formData.ticker !== tickerInput && (
              <p className="text-xs text-gray-500 mt-1">Nenhum ativo encontrado para "{tickerInput}"</p>
            )}
          </div>

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

          {/* Data + Taxas */}
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Data"
              type="date"
              value={formData.transactionDate}
              onChange={(e) => setFormData({...formData, transactionDate: e.target.value})}
              required
            />
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
          </div>

          {/* Total (readonly) */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-1.5">Total (R$)</label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">R$</span>
              <input
                type="text"
                readOnly
                className="flex h-10 w-full rounded-md border border-surface-light bg-surface-light/50 pl-9 pr-3 py-2 text-sm text-white font-semibold cursor-default"
                value={toCurrencyDisplay(computedTotal)}
              />
            </div>
          </div>

          <div className="pt-4 flex justify-end gap-3 border-t border-surface-light">
            <Button type="button" variant="ghost" onClick={() => setIsModalOpen(false)}>Cancelar</Button>
            <Button type="submit" isLoading={isSaving}>Salvar</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
