import { apiClient } from './client';
import { NotificationItem, NotificationPage, NotificationStateAction, NotificationSummary } from '../types';

export interface NotificationFilters {
  folder: 'INBOX' | 'ARCHIVED';
  audience?: 'USER' | 'ADMIN';
  category?: string;
  /** NAO_RESOLVIDA, DISPENSADA, RESOLVIDA ou INFORMATIVA (sem status). */
  status?: string;
  unread?: boolean;
  page?: number;
}

/** A caixa de notificações (docs/planos/PLANO_NOTIFICACOES.md, 5.2). */
export const notificationsApi = {
  list: async (f: NotificationFilters) => (await apiClient.get<NotificationPage>('/notifications', {
    params: {
      folder: f.folder,
      audience: f.audience || undefined,
      category: f.category || undefined,
      status: f.status || undefined,
      unread: f.unread || undefined,
      page: f.page ?? 0,
    },
  })).data,
  summary: async () => (await apiClient.get<NotificationSummary>('/notifications/summary')).data,
  /** Abrir é o único caminho para "lida". */
  open: async (id: string) => (await apiClient.post<NotificationItem>(`/notifications/${id}/open`)).data,
  state: async (ids: string[], action: NotificationStateAction) =>
    (await apiClient.patch<{ changed: number }>('/notifications/state', { ids, action })).data,
  status: async (id: string, action: 'DISMISS' | 'REOPEN') =>
    (await apiClient.patch<NotificationItem>(`/notifications/${id}/status`, { action })).data,
};
