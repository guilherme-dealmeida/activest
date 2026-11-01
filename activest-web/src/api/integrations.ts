import { apiClient } from './client';
import {
  ConnectionCreate, CryptoExclusion, DuplicateDecision, ImportRun, IntegrationConnection, IntegrationSource, RunSummary,
  TransactionResponse
} from '../types';

const BASE = '/integrations/connections';

/**
 * Integrações com corretoras (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.7).
 *
 * A chave e o segredo só passam por connect(): nada aqui os guarda, e nenhuma
 * resposta os devolve — da chave volta só o fingerprint (4 últimos caracteres).
 */
export const integrationsApi = {
  /** Todas as fontes que o Activest conhece; `available` diz se estão ligadas no servidor. */
  sources: async () => (await apiClient.get<IntegrationSource[]>('/integrations/sources')).data,
  list: async () => (await apiClient.get<IntegrationConnection[]>(BASE)).data,
  connect: async (body: ConnectionCreate) => (await apiClient.post<IntegrationConnection>(BASE, body)).data,
  disconnect: async (id: string) => { await apiClient.delete(`${BASE}/${id}`); },
  /** Entra na fila (202) e volta na hora; acompanhe por getRun. */
  sync: async (id: string) => (await apiClient.post<ImportRun>(`${BASE}/${id}/preview`)).data,
  runs: async (id: string) => (await apiClient.get<RunSummary[]>(`${BASE}/${id}/runs`)).data,
  getRun: async (id: string, runId: string) => (await apiClient.get<ImportRun>(`${BASE}/${id}/runs/${runId}`)).data,
  confirm: async (id: string, runId: string, duplicates: Record<string, DuplicateDecision>) =>
    (await apiClient.post<ImportRun>(`${BASE}/${id}/runs/${runId}/confirm`, { duplicates })).data,
  cancel: async (id: string, runId: string) => { await apiClient.post(`${BASE}/${id}/runs/${runId}/cancel`); },
  exclusions: async (id: string) => (await apiClient.get<CryptoExclusion[]>(`${BASE}/${id}/exclusions`)).data,
  restoreExclusion: async (id: string, exclusionId: string) =>
    (await apiClient.post<TransactionResponse>(`${BASE}/${id}/exclusions/${exclusionId}/restore`)).data,
};
