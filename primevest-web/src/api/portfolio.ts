import { apiClient } from './client';
import {
  PortfolioSummary,
  PortfolioAssetDetail,
  PortfolioAllocation,
  PortfolioAllocationDetailed,
  PortfolioPerformance,
} from '@/types';

/**
 * Os tipos de retorno espelham os DTOs de PortfolioController, campo a campo.
 * Sem eles o axios devolve any, o useQuery propaga unknown e a tela inteira
 * perde a checagem — foi assim que o PerformanceLineChart passou meses lendo
 * data.dates, um campo que a API nunca devolveu, sem ninguém perceber.
 *
 * Ao mexer num DTO do backend, mexa na interface correspondente em types/:
 * é esse par que faz o tsc acusar a divergência em vez de ela virar undefined
 * em produção.
 */
export const portfolioApi = {
  getSummary: async (): Promise<PortfolioSummary> => {
    const { data } = await apiClient.get<PortfolioSummary>('/portfolio/');
    return data;
  },

  getAssets: async (): Promise<PortfolioAssetDetail[]> => {
    const { data } = await apiClient.get<PortfolioAssetDetail[]>('/portfolio/assets');
    return data;
  },

  getAllocation: async (): Promise<PortfolioAllocation[]> => {
    const { data } = await apiClient.get<PortfolioAllocation[]>('/portfolio/allocation');
    return data;
  },

  getAllocationDetailed: async (): Promise<PortfolioAllocationDetailed[]> => {
    const { data } = await apiClient.get<PortfolioAllocationDetailed[]>('/portfolio/allocation/detailed');
    return data;
  },

  getPerformance: async (): Promise<PortfolioPerformance> => {
    const { data } = await apiClient.get<PortfolioPerformance>('/portfolio/performance');
    return data;
  },

  /**
   * GET /portfolio/dividends hoje é um esboço: o controller responde
   * ResponseEntity.ok().build(), ou seja, 200 sem corpo nenhum. O tipo void diz
   * isso na cara — quem chamar não vai supor que recebe uma lista.
   * Os proventos de verdade estão em dividendsApi.
   */
  getDividends: async (): Promise<void> => {
    await apiClient.get('/portfolio/dividends');
  },
};
