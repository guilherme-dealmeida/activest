import { apiClient } from './client';
import { CompanyQuarter, CompanyYear, FiiMonth, Issuer } from '../types';

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
  /** Demonstrativos no Anual: exercícios fechados e o ano em andamento (docs/planos/PLANO_TELA_ATIVOS.md, D8). */
  years: async (id: number) => {
    const { data } = await apiClient.get<CompanyYear[]>(`/issuers/${id}/years`);
    return data;
  },
  months: async (id: number) => {
    const { data } = await apiClient.get<FiiMonth[]>(`/issuers/${id}/months`);
    return data;
  },
};
