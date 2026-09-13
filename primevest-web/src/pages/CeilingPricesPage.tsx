import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ceilingPricesApi } from '@/api/ceilingPrices';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Badge } from '@/components/ui/Badge';
import { Pagination } from '@/components/ui/Pagination';
import { formatCurrency, formatPercentage } from '@/utils/format';
import { CeilingPrice, CeilingPriceCreate } from '@/types';
import { Plus, Trash2, Edit2 } from 'lucide-react';
import { useSortable } from '@/hooks/useSortable';
import { getAssetType, getAllAssetTypes } from '@/utils/assetTypes';

export const CeilingPricesPage = () => {
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [filterType, setFilterType] = useState('ALL');
  const [formData, setFormData] = useState<CeilingPriceCreate>({
    ticker: '',
    maxPrice: 0,
    alertEnabled: true
  });

  const { data: prices, isLoading } = useQuery<CeilingPrice[]>({
    queryKey: ['ceilingPrices'],
    queryFn: ceilingPricesApi.list as any
  });

  const mutationCreate = useMutation({
    mutationFn: ceilingPricesApi.create as any,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['ceilingPrices'] }); closeModal(); }
  });

  const mutationUpdate = useMutation({
    mutationFn: ({ id, data }: { id: string, data: CeilingPriceCreate }) => ceilingPricesApi.update(id, data as any) as any,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['ceilingPrices'] }); closeModal(); }
  });

  const mutationDelete = useMutation({
    mutationFn: ceilingPricesApi.delete as any,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ceilingPrices'] })
  });

  // Enrich data with assetType for sorting
  const enriched = (prices || []).map(p => ({ ...p, assetType: getAssetType(p.ticker) }));
  const filtered = enriched.filter(p => filterType === 'ALL' || p.assetType === filterType);
  const { sortedData, requestSort, getSortIndicator, getSortClass } = useSortable(filtered);
  const totalPages = Math.ceil(sortedData.length / itemsPerPage);
  const paginated = sortedData.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  const openModal = (item?: CeilingPrice) => {
    if (item) {
      setEditingId(item.id);
      setFormData({ ticker: item.ticker, maxPrice: item.maxPrice, alertEnabled: item.alertEnabled });
    } else {
      setEditingId(null);
      setFormData({ ticker: '', maxPrice: 0, alertEnabled: true });
    }
    setIsModalOpen(true);
  };

  const closeModal = () => { setIsModalOpen(false); setEditingId(null); };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (editingId) mutationUpdate.mutate({ id: editingId, data: formData });
    else mutationCreate.mutate(formData as any);
  };

  if (isLoading) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-end">
        <div className="flex items-end gap-3">
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
          <p className="text-gray-400 text-sm pb-2">Monitore seus ativos baseados no seu preço teto pessoal.</p>
        </div>
        <Button onClick={() => openModal()}>
          <Plus className="w-4 h-4 mr-2" />
          Novo Preço Teto
        </Button>
      </div>

      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-gray-400 uppercase border-b border-white/10">
              <tr>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('assetType')}`} onClick={() => requestSort('assetType')}>
                  TIPO ATIVO{getSortIndicator('assetType')}
                </th>
                <th className={`px-4 py-3 cursor-pointer ${getSortClass('ticker')}`} onClick={() => requestSort('ticker')}>
                  ATIVO{getSortIndicator('ticker')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('currentPrice')}`} onClick={() => requestSort('currentPrice')}>
                  PREÇO ATUAL{getSortIndicator('currentPrice')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('maxPrice')}`} onClick={() => requestSort('maxPrice')}>
                  PREÇO TETO{getSortIndicator('maxPrice')}
                </th>
                <th className={`px-4 py-3 text-right cursor-pointer ${getSortClass('diffPercentage')}`} onClick={() => requestSort('diffPercentage')}>
                  DIFERENÇA %{getSortIndicator('diffPercentage')}
                </th>
                <th className={`px-4 py-3 text-center cursor-pointer ${getSortClass('belowCeiling')}`} onClick={() => requestSort('belowCeiling')}>
                  STATUS{getSortIndicator('belowCeiling')}
                </th>
                <th className="px-4 py-3 text-right">AÇÕES</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {paginated.map((row) => (
                <tr key={row.id} className="hover:bg-white/5 transition-colors">
                  <td className="px-4 py-3 text-gray-400">{row.assetType}</td>
                  <td className="px-4 py-3 font-medium text-white">{row.ticker}</td>
                  <td className="px-4 py-3 text-right">{formatCurrency(row.currentPrice)}</td>
                  <td className="px-4 py-3 text-right">{formatCurrency(row.maxPrice)}</td>
                  <td className="px-4 py-3 text-right">
                    <span className={row.diffPercentage < 0 ? 'text-success' : 'text-danger'}>{formatPercentage(Math.abs(row.diffPercentage))}</span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <Badge variant={row.belowCeiling ? 'success' : 'danger'}>
                      {row.belowCeiling ? 'Abaixo do Teto' : 'Acima do Teto'}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-2">
                      <button onClick={() => openModal(row)} className="text-gray-400 hover:text-white p-1"><Edit2 className="w-4 h-4" /></button>
                      <button onClick={() => { if(window.confirm('Excluir?')) mutationDelete.mutate(row.id as any); }} className="text-gray-400 hover:text-danger p-1"><Trash2 className="w-4 h-4" /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {paginated.length === 0 && (
                <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-500">Nenhum preço teto encontrado.</td></tr>
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

      <Modal isOpen={isModalOpen} onClose={closeModal} title={editingId ? "Editar Preço Teto" : "Novo Preço Teto"}>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Input
            label="Ticker (Ativo)"
            value={formData.ticker}
            onChange={(e) => setFormData({...formData, ticker: e.target.value.toUpperCase()})}
            required
            disabled={!!editingId}
          />
          <Input
            label="Preço Teto (R$)"
            type="number"
            step="any"
            value={formData.maxPrice || ''}
            onChange={(e) => setFormData({...formData, maxPrice: Number(e.target.value)})}
            required
          />
          <div className="pt-4 flex justify-end gap-3">
            <Button type="button" variant="ghost" onClick={closeModal}>Cancelar</Button>
            <Button type="submit" isLoading={mutationCreate.isPending || mutationUpdate.isPending}>Salvar</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
