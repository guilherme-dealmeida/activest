import { apiClient } from './client';
import { GoalCreate, GoalUpdate } from '../types';

export const goalsApi = {
  list: async () => { const { data } = await apiClient.get('/goals/'); return data; },
  create: async (goalData: GoalCreate) => { const { data } = await apiClient.post('/goals/', goalData); return data; },
  update: async (id: string, goalData: GoalUpdate) => { const { data } = await apiClient.put(`/goals/${id}`, goalData); return data; },
  delete: async (id: string) => { await apiClient.delete(`/goals/${id}`); },
};
