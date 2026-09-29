import { apiClient } from './client';
import { CompanyQuarter, FiiMonth, Issuer } from '../types';

/** Emissor do catálogo: cadastro, série trimestral (companhia) e informe mensal (FII). */
export const issuersApi = {
  get: async (id: number) => {
    const { data } = await apiClient.get<Issuer>(`/issuers/${id}`);
    return data;
  },
  quarters: async (id: number) => {
    const { data } = await apiClient.get<CompanyQuarter[]>(`/issuers/${id}/quarters`);
    return data;
  },
  months: async (id: number) => {
    const { data } = await apiClient.get<FiiMonth[]>(`/issuers/${id}/months`);
    return data;
  },
};
