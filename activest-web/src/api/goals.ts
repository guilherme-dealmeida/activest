import { apiClient } from './client';
import {
  Goal, GoalAssumption, GoalCatalog, GoalEvent, GoalHistory, GoalList, GoalPreview, GoalRequest, GoalScopeOptions,
} from '../types/goals';

export type GoalAction = 'pause' | 'resume' | 'complete' | 'archive' | 'unarchive';

/** Metas (docs/planos/PLANO_METAS.md, Anexo A 11). */
export const goalsApi = {
  catalog: async () => (await apiClient.get<GoalCatalog>('/goals/types')).data,
  scopeOptions: async () => (await apiClient.get<GoalScopeOptions>('/goals/scope-options')).data,
  list: async (lifecycle?: string) =>
    (await apiClient.get<GoalList>('/goals', { params: lifecycle ? { lifecycle } : {} })).data,
  get: async (id: string) => (await apiClient.get<Goal>(`/goals/${id}`)).data,
  preview: async (req: GoalRequest) => (await apiClient.post<GoalPreview>('/goals/preview', req)).data,
  /** Valores automáticos das premissas (sem validar o cadastro): o formulário já vem com eles. */
  assumptions: async (req: GoalRequest) => (await apiClient.post<GoalAssumption[]>('/goals/assumptions', req)).data,
  create: async (req: GoalRequest) => (await apiClient.post<Goal>('/goals', req)).data,
  update: async (id: string, req: GoalRequest) => (await apiClient.put<Goal>(`/goals/${id}`, req)).data,
  transition: async (id: string, action: GoalAction) => (await apiClient.post<Goal>(`/goals/${id}/${action}`)).data,
  remove: async (id: string) => { await apiClient.delete(`/goals/${id}`); },
  history: async (id: string) => (await apiClient.get<GoalHistory>(`/goals/${id}/history`)).data,
  events: async (id: string) => (await apiClient.get<GoalEvent[]>(`/goals/${id}/events`)).data,
};
