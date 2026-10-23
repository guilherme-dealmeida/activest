import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { tasksApi } from '@/api/tasks';
import { TaskLogLine } from '@/types';
import { formatDateTime, formatDuration, TRIGGER_HINT } from './taskFormat';
import { StatusBadge } from './StatusBadge';

const LEVELS = ['', 'INFO', 'WARN', 'ERROR'] as const;
const LEVEL_CLASS: Record<TaskLogLine['level'], string> = {
  INFO: 'text-gray-400',
  WARN: 'text-orange-300',
  ERROR: 'text-danger',
};

/**
 * Ver log de uma rodada (D6): resumo e erro no topo, os atributos usados e as linhas que a rotina escreveu no log,
 * com filtro por nível.
 */
export const TaskExecutionModal = ({ executionId, onClose }: { executionId: number | null; onClose: () => void }) => {
  const [level, setLevel] = useState<string>('');
  const [pages, setPages] = useState(1);

  const detail = useQuery({
    queryKey: ['tasks', 'execution', executionId],
    queryFn: () => tasksApi.execution(executionId as number),
    enabled: executionId != null,
    refetchInterval: (q) => (q.state.data?.execution.status === 'RUNNING' ? 5000 : false),
  });
  const running = detail.data?.execution.status === 'RUNNING';
  const logs = useQuery({
    queryKey: ['tasks', 'execution', executionId, 'logs', level, pages],
    queryFn: async () => {
      const all: TaskLogLine[] = [];
      let total = 0;
      for (let p = 0; p < pages; p++) {
        const page = await tasksApi.logs(executionId as number, level || undefined, p);
        all.push(...page.lines);
        total = page.total;
      }
      return { lines: all, total };
    },
    enabled: executionId != null,
    refetchInterval: running ? 5000 : false,
  });

  if (executionId == null) return null;
  const e = detail.data?.execution;

  return (
    <Modal isOpen onClose={onClose} title={e ? `Rodada ${e.id} — ${e.taskName}` : 'Rodada'} size="2xl">
      {!detail.data ? (
        <div className="flex justify-center py-10"><LoadingSpinner className="h-6 w-6 text-accent" /></div>
      ) : (
        <div className="space-y-5 text-sm">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <Info label="Status"><StatusBadge status={detail.data.execution.status} /></Info>
            <Info label="Origem">
              <span className="cursor-help" title={TRIGGER_HINT[detail.data.execution.triggerType]}>
                {detail.data.execution.triggerType}
              </span>
            </Info>
            <Info label="Vigência">{formatDateTime(detail.data.execution.scheduledFor)}</Info>
            <Info label="Pedido por">{detail.data.execution.requestedBy ?? 'Sistema'}</Info>
            <Info label="Início">{formatDateTime(detail.data.execution.startedAt, true)}</Info>
            <Info label="Fim">{formatDateTime(detail.data.execution.finishedAt, true) || '—'}</Info>
            <Info label="Duração">{formatDuration(detail.data.execution.durationSeconds)}</Info>
            <Info label="Stop">
              {detail.data.execution.stopRequestedAt
                ? `${formatDateTime(detail.data.execution.stopRequestedAt, true)}${detail.data.stopRequestedBy ? ` por ${detail.data.stopRequestedBy}` : ''}`
                : '—'}
            </Info>
          </div>

          {detail.data.execution.summary && (
            <div>
              <p className="text-xs text-gray-400 mb-1">Resumo</p>
              <p className="rounded-md bg-surface-light/40 p-3 text-gray-200 break-words">{detail.data.execution.summary}</p>
            </div>
          )}
          {detail.data.execution.errorMessage && (
            <div>
              <p className="text-xs text-gray-400 mb-1">{detail.data.execution.status === 'FAILED' ? 'Erro' : 'Motivo'}</p>
              <p className="rounded-md border border-danger/30 bg-danger/10 p-3 text-gray-200 break-words">
                {detail.data.execution.errorMessage}
              </p>
            </div>
          )}

          {Object.keys(detail.data.parameters).length > 0 && (
            <div>
              <p className="text-xs text-gray-400 mb-1">Atributos usados</p>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-1">
                {Object.entries(detail.data.parameters).map(([k, v]) => (
                  <div key={k} className="flex gap-2 font-mono text-xs">
                    <span className="text-gray-400">{k}</span>
                    <span className="text-gray-200 break-all">{v == null || v === '' ? '—' : v}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div>
            <div className="flex items-center gap-3 mb-2">
              <p className="text-xs text-gray-400">
                Log da rodada ({logs.data?.total ?? detail.data.logLines} linha(s)
                {detail.data.execution.logLinesOmitted > 0 && `; ${detail.data.execution.logLinesOmitted} linha(s) INFO omitidas pelo teto`})
              </p>
              <select
                className="ml-auto bg-surface-light border border-white/10 text-white text-xs rounded-md p-1.5"
                value={level}
                onChange={(ev) => { setLevel(ev.target.value); setPages(1); }}
              >
                {LEVELS.map(l => <option key={l} value={l}>{l || 'Todos os níveis'}</option>)}
              </select>
            </div>
            <div className="max-h-80 overflow-y-auto rounded-md bg-black/40 border border-white/10 p-3 font-mono text-xs space-y-0.5">
              {logs.isLoading && <LoadingSpinner className="h-4 w-4 text-accent" />}
              {logs.data?.lines.length === 0 && <p className="text-gray-500">Nenhuma linha.</p>}
              {logs.data?.lines.map(l => (
                <div key={l.id} className="flex gap-2">
                  <span className="text-gray-500 shrink-0">{formatDateTime(l.loggedAt, true).slice(11)}</span>
                  <span className={`shrink-0 w-11 ${LEVEL_CLASS[l.level]}`}>{l.level}</span>
                  <span className="text-gray-200 break-all whitespace-pre-wrap">{l.message}</span>
                </div>
              ))}
            </div>
            {logs.data && logs.data.lines.length < logs.data.total && (
              <div className="mt-2 flex justify-center">
                <Button type="button" variant="outline" size="sm" onClick={() => setPages(p => p + 1)}>
                  Carregar mais
                </Button>
              </div>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
};

const Info = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <div>
    <p className="text-xs text-gray-400 mb-0.5">{label}</p>
    <div className="text-gray-200">{children}</div>
  </div>
);
