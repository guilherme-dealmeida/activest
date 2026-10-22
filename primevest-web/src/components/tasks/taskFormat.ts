import { TaskExecutionStatus, TaskTriggerType } from '@/types';

/**
 * As datas das tarefas vêm como LocalDateTime de São Paulo ("2026-10-22T15:26:05.99"): lidas como texto, para o fuso
 * do navegador não deslocar a hora.
 */
export const formatDateTime = (value?: string | null, withSeconds = false): string => {
  if (!value) return '';
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/.exec(value);
  if (!m) return value;
  const [, y, mo, d, h, mi, s] = m;
  return `${d}/${mo}/${y} ${h}:${mi}${withSeconds ? `:${s ?? '00'}` : ''}`;
};

export const formatDuration = (seconds?: number | null): string => {
  if (seconds == null) return '';
  if (seconds < 60) return `${seconds} s`;
  const m = Math.floor(seconds / 60);
  if (m < 60) return `${m} min ${seconds % 60} s`;
  return `${Math.floor(m / 60)} h ${m % 60} min`;
};

/** D2: o status aparece em inglês também na tela; a cor diz a situação. */
export const STATUS_CLASS: Record<TaskExecutionStatus, string> = {
  RUNNING: 'bg-blue-500/15 text-blue-300 border-blue-500/30',
  SUCCEEDED: 'bg-success/15 text-success border-success/30',
  FAILED: 'bg-danger/15 text-danger border-danger/30',
  CANCELLED: 'bg-orange-500/15 text-orange-300 border-orange-500/30',
  SKIPPED: 'bg-gray-500/15 text-gray-300 border-gray-500/30',
  SCHEDULED: 'bg-accent/10 text-accent border-accent/30',
};

export const STATUS_HINT: Record<TaskExecutionStatus, string> = {
  RUNNING: 'Executando.',
  SUCCEEDED: 'Executou com sucesso.',
  FAILED: 'Houve uma falha na execução.',
  CANCELLED: 'Encerrada a pedido (Stop) antes do fim.',
  SKIPPED: 'Não rodou: já havia uma rodada do mesmo tipo em execução.',
  SCHEDULED: 'Agendada: calculada do horário da tarefa.',
};

export const TRIGGER_HINT: Record<TaskTriggerType, string> = {
  SCHEDULE: 'Pelo horário da tarefa.',
  MANUAL: 'Pedido na tela ou nos endpoints de administrador.',
  STARTUP: 'Recuperação na subida do serviço.',
};

export const ALL_STATUSES: TaskExecutionStatus[] = ['SCHEDULED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'SKIPPED'];
