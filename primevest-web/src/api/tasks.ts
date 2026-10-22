import { apiClient } from './client';
import {
  TaskExecutionDetail, TaskExecutionItem, TaskItem, TaskLogPage, TaskRunResponse, TaskUpdate,
} from '../types';

export interface TaskExecutionFilters {
  /** aaaa-mm-dd; ausente = 7 dias para trás (D35). */
  from?: string;
  /** aaaa-mm-dd; ausente = 7 dias para frente. */
  to?: string;
  type?: string;
  status?: string;
}

/** Tela de Tarefas (docs/planos/PLANO_TAREFAS.md, T5): o proxy manda /api/v1/tasks para a adm-api (D9). */
export const tasksApi = {
  list: async () => (await apiClient.get<TaskItem[]>('/tasks')).data,
  update: async (id: number, body: TaskUpdate) => (await apiClient.put<TaskItem>(`/tasks/${id}`, body)).data,
  run: async (id: number) => (await apiClient.post<TaskRunResponse>(`/tasks/${id}/run`)).data,
  executions: async (f: TaskExecutionFilters) => (await apiClient.get<TaskExecutionItem[]>('/tasks/executions', {
    params: {
      from: f.from || undefined,
      to: f.to || undefined,
      type: f.type || undefined,
      status: f.status || undefined,
    },
  })).data,
  execution: async (id: number) => (await apiClient.get<TaskExecutionDetail>(`/tasks/executions/${id}`)).data,
  logs: async (id: number, level: string | undefined, page: number) =>
    (await apiClient.get<TaskLogPage>(`/tasks/executions/${id}/logs`, {
      params: { level: level || undefined, page, size: 200 },
    })).data,
  stop: async (id: number) => { await apiClient.post(`/tasks/executions/${id}/stop`); },
};
