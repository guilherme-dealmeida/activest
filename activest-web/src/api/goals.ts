import { apiClient } from './client';
import { GoalCreate, GoalResponse, GoalUpdate } from '../types';

export const goalsApi = {
  list: async () => { const { data } = await apiClient.get<GoalResponse[]>('/goals/'); return data; },
  create: async (goalData: GoalCreate) => { const { data } = await apiClient.post<GoalResponse>('/goals/', goalData); return data; },
  update: async (id: string, goalData: GoalUpdate) => { const { data } = await apiClient.put<GoalResponse>(`/goals/${id}`, goalData); return data; },
  delete: async (id: string) => { await apiClient.delete(`/goals/${id}`); },
};
