import { apiClient } from './client';
import { NoteOption, SalePreview, TransactionCreate, TransactionResponse } from '../types';

export const transactionsApi = {
  list: async () => { const { data } = await apiClient.get<TransactionResponse[]>('/transactions/'); return data; },
  create: async (txData: TransactionCreate) => { const { data } = await apiClient.post<TransactionResponse>('/transactions/', txData); return data; },
  update: async (id: string, txData: TransactionCreate) => { const { data } = await apiClient.put<TransactionResponse>(`/transactions/${id}`, txData); return data; },
  getById: async (id: string) => { const { data } = await apiClient.get<TransactionResponse>(`/transactions/${id}`); return data; },
  delete: async (id: string) => { await apiClient.delete(`/transactions/${id}`); },
  /** Títulos de renda fixa do usuário no ativo — todos, inclusive liquidados (seletor da Nova Transação). */
  notes: async (ticker: string) => { const { data } = await apiClient.get<NoteOption[]>('/transactions/notes', { params: { ticker } }); return data; },
  /** Regra E4: volta a transação importada aos valores da corretora. */
  restore: async (id: string) => { const { data } = await apiClient.post<TransactionResponse>(`/transactions/${id}/restore`); return data; },
  /**
   * Prévia da venda de renda fixa (o valor é o LÍQUIDO) ou de Tesouro, sem gravar. Na edição, excludeId é a própria
   * venda, que fica de fora da conta.
   */
  salePreview: async (txData: TransactionCreate, excludeId?: string | null) => {
    const { data } = await apiClient.post<SalePreview>('/transactions/sale-preview', txData,
      { params: excludeId ? { excludeId } : undefined });
    return data;
  },
};
