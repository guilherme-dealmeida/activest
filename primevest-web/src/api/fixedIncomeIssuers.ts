import { apiClient } from './client';
import { FixedIncomeIssuerOption } from '../types';

/**
 * Cadastro de emissoras e distribuidoras de renda fixa (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, seção 6).
 * Emissora: só os tipos que o produto aceita (CDB → bancos); distribuidora: corretoras, DTVMs, bancos, financeiras e
 * cooperativas. "Instituição não listada" vem sempre por último.
 */
export const fixedIncomeIssuersApi = {
  search: async (role: 'ISSUER' | 'DISTRIBUTOR', q: string, ticker?: string | null, limit = 30) => {
    const { data } = await apiClient.get<FixedIncomeIssuerOption[]>('/fixed-income-issuers', {
      params: { role, q, ...(ticker ? { ticker } : {}), limit },
    });
    return data;
  },
};
