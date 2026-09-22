import { apiClient } from './client';
import { StrategyReservation, StrategyReservationUpdate, StrategyUpdate } from '../types';

export const strategyApi = {
  get: async () => { const { data } = await apiClient.get('/strategy/'); return data; },
  update: async (stratData: StrategyUpdate) => { const { data } = await apiClient.put('/strategy/', stratData); return data; },
  delete: async (assetTypeId: number) => { await apiClient.delete(`/strategy/${assetTypeId}`); },
  getRebalance: async () => { const { data } = await apiClient.get('/strategy/rebalance'); return data; },
  getAssetTypes: async () => { const { data } = await apiClient.get('/assets/types'); return data; },

  /** Estratégia da reserva de emergência, com os números apurados da carteira. */
  getReservation: async (): Promise<StrategyReservation> => {
    const { data } = await apiClient.get<StrategyReservation>('/strategy/reservation');
    return data;
  },

  /** Não existe exclusão: a estratégia de reserva só é lida e atualizada. */
  updateReservation: async (payload: StrategyReservationUpdate): Promise<StrategyReservation> => {
    const { data } = await apiClient.put<StrategyReservation>('/strategy/reservation', payload);
    return data;
  },
};
