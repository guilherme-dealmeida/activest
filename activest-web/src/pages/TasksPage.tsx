import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { FileText, Pencil, Play, Plus, RefreshCw, Square, Trash2 } from 'lucide-react';
import { clsx } from 'clsx';
import { tasksApi } from '@/api/tasks';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Pagination } from '@/components/ui/Pagination';
import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { TABLE_ACTION_CLASS } from '@/components/ui/tableAction';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';
import { describeCron } from '@/utils/cron';
import { toLocalISODate } from '@/utils/format';
import { TaskEditModal } from '@/components/tasks/TaskEditModal';
import { TaskExecutionModal } from '@/components/tasks/TaskExecutionModal';
import { ALL_STATUSES, formatDateTime, formatDuration, STATUS_HINT, TRIGGER_HINT } from '@/components/tasks/taskFormat';
import { StatusBadge } from '@/components/tasks/StatusBadge';
import { useSortable } from '@/hooks/useSortable';
import { TaskExecutionItem, TaskExecutionStatus, TaskItem } from '@/types';

type TaskTab = 'monitoring' | 'schedules' | 'configuration';

const TAB_LABELS: Record<TaskTab, string> = {
  monitoring: 'Execuções',
  schedules: 'Agendamentos',
  configuration: 'Configuração',
};

const SOON = 'Em breve: cadastrar e excluir tarefas ainda não têm efetividade (docs/planos/PLANO_TAREFAS.md, D15).';

const selectClass =
  'bg-surface-light border border-white/10 text-white text-sm rounded-md focus:ring-accent focus:border-accent p-2';

const addDays = (days: number) => {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return toLocalISODate(d);
};

/**
 * Tarefas (docs/planos/PLANO_TAREFAS.md): só administrador (D14). Abas Acompanhamento (as rodadas, com Run, Stop e
 * o log), Agendamentos (as próximas execuções calculadas do horário) e Configuração (as tarefas, com Run e Editar). A
 * aba fica na URL (`?tab=monitoring|schedules|configuration`); o link da mensagem de rotina que falhou abre a rodada
 * (`?execution=`).
 */
export const TasksPage = () => {
  const [params, setParams] = useSearchParams();
  const tabParam = params.get('tab');
  const activeTab: TaskTab = tabParam === 'configuration' || tabParam === 'schedules' ? tabParam : 'monitoring';
  const executionParam = params.get('execution');
  const [openExecution, setOpenExecution] = useState<number | null>(executionParam ? Number(executionParam) : null);

  const setTab = (tab: TaskTab) => {
    const next = new URLSearchParams(params);
    next.set('tab', tab);
    next.delete('execution');
    setParams(next, { replace: true });
  };

  const closeExecution = () => {
    setOpenExecution(null);
    if (params.has('execution')) {
      const next = new URLSearchParams(params);
      next.delete('execution');
      setParams(next, { replace: true });
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex space-x-1 bg-surface p-1 rounded-lg w-fit border border-surface-light">
        {(Object.keys(TAB_LABELS) as TaskTab[]).map(tab => (
          <button
            key={tab}
            onClick={() => setTab(tab)}
            className={clsx(
              'px-4 py-2 text-sm font-medium rounded-md transition-colors',
              activeTab === tab ? 'bg-surface-light text-white' : 'text-gray-400 hover:text-white hover:bg-surface-light/50'
            )}
          >
            {TAB_LABELS[tab]}
          </button>
        ))}
      </div>

      <div hidden={activeTab !== 'monitoring'}>
        <ExecutionsTab scheduled={false} onOpenExecution={setOpenExecution} />
      </div>
      <div hidden={activeTab !== 'schedules'}>
        <ExecutionsTab scheduled onOpenExecution={setOpenExecution} />
      </div>
      <div hidden={activeTab !== 'configuration'}>
        <ConfigurationTab onOpenExecution={setOpenExecution} />
      </div>

      <TaskExecutionModal executionId={openExecution} onClose={closeExecution} />
    </div>
  );
};

/** Run com confirmação (as recargas são longas) e Stop, compartilhados pelas duas abas. */
const useTaskActions = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['tasks'] });

  const run = useMutation({
    mutationFn: (task: { id: number; name: string }) => tasksApi.run(task.id),
    onSuccess: (r, task) => {
      showToast(r.status === 'SKIPPED' ? `${task.name}: ${r.message}` : `${task.name}: rodada ${r.executionId} iniciada.`,
        r.status === 'SKIPPED' ? 'info' : 'success', r.status === 'SKIPPED' ? 8000 : 4000);
      refresh();
    },
    onError: (e, task) => showToast(apiMessage(e) ?? `Não foi possível iniciar ${task.name}.`, 'error', 8000),
  });

  const stop = useMutation({
    mutationFn: (execution: TaskExecutionItem) => tasksApi.stop(execution.id as number),
    onSuccess: (_, execution) => {
      showToast(`${execution.taskName}: Stop pedido — a rotina para no próximo ponto seguro.`, 'info');
      refresh();
    },
    onError: (e) => showToast(apiMessage(e) ?? 'Não foi possível pedir o Stop.', 'error', 8000),
  });

  return { run, stop };
};

const RunConfirm = ({ task, onConfirm, onClose, pending }: {
  task: { id: number; name: string; attributes?: TaskItem['attributes'] } | null;
  onConfirm: () => void; onClose: () => void; pending: boolean;
}) => {
  if (!task) return null;
  const filled = (task.attributes ?? []).filter(a => a.value != null && a.value !== '');
  return (
    <Modal
      isOpen
      onClose={onClose}
      title={`Executar ${task.name}?`}
      footer={
        <div className="flex justify-end gap-3">
          <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
          <Button type="button" onClick={onConfirm} isLoading={pending}>
            <Play className="w-4 h-4 mr-1.5" />Run
          </Button>
        </div>
      }
    >
      <div className="space-y-3 text-sm text-gray-300">
        <p>A rodada começa agora, com os atributos da tarefa. Recargas de período longo podem levar dezenas de minutos.</p>
        {filled.length > 0 && (
          <div className="rounded-md bg-surface-light/40 p-3 font-mono text-xs space-y-0.5">
            {filled.map(a => <div key={a.name}><span className="text-gray-400">{a.name}</span> {a.value}</div>)}
          </div>
        )}
      </div>
    </Modal>
  );
};

// ------------------------------------------------------------------------------------ Acompanhamento e Agendamentos

/** Tags de status do Acompanhamento: as marcadas entram no filtro; por padrão, todas menos SCHEDULED. */
const MONITORING_STATUSES = ALL_STATUSES.filter(s => s !== 'SCHEDULED');

const StatusFlags = ({ selected, onToggle }: {
  selected: Set<TaskExecutionStatus>; onToggle: (status: TaskExecutionStatus) => void;
}) => (
  <div className="flex flex-wrap items-center gap-2">
    {ALL_STATUSES.map(s => {
      const on = selected.has(s);
      return (
        <button
          key={s}
          type="button"
          aria-pressed={on}
          onClick={() => onToggle(s)}
          title={`${on ? 'Ocultar' : 'Mostrar'} ${s} — ${STATUS_HINT[s]}`}
          className={clsx(
            'inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium transition-colors',
            on
              ? 'border-accent/40 bg-accent/15 text-accent hover:bg-accent/25'
              : 'border-white/10 bg-surface-light/40 text-gray-500 hover:text-gray-300'
          )}
        >
          <span className={clsx('h-1.5 w-1.5 rounded-full', on ? 'bg-accent' : 'bg-gray-600')} />
          {s}
        </button>
      );
    })}
  </div>
);

/** A linha com o resumo (ou o erro) num campo só, para a coluna RESUMO ordenar pelo que mostra. */
type ExecutionRow = TaskExecutionItem & { resume?: string };

/**
 * Acompanhamento (`scheduled` falso): as rodadas do período, filtradas pelas tags de status; ordem padrão vigência
 * decrescente. Agendamentos (`scheduled`): só as agendadas (SCHEDULED), sem a coluna STATUS; ordem padrão vigência
 * crescente. As duas com De, Até e Tipo, e as colunas ordenáveis.
 */
const ExecutionsTab = ({ scheduled, onOpenExecution }: {
  scheduled: boolean; onOpenExecution: (id: number) => void;
}) => {
  const [from, setFrom] = useState(scheduled ? addDays(0) : addDays(-1));
  const [to, setTo] = useState(addDays(0));
  const [type, setType] = useState('');
  const [selected, setSelected] = useState<Set<TaskExecutionStatus>>(() => new Set(MONITORING_STATUSES));
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(25);
  const [confirm, setConfirm] = useState<{ id: number; name: string } | null>(null);
  const { run, stop } = useTaskActions();

  const statuses = useMemo(
    () => (scheduled ? ['SCHEDULED'] : ALL_STATUSES.filter(s => selected.has(s))),
    [scheduled, selected]
  );
  const toggleStatus = (s: TaskExecutionStatus) => setSelected(prev => {
    const next = new Set(prev);
    if (next.has(s)) next.delete(s); else next.add(s);
    return next;
  });

  const executions = useQuery({
    queryKey: ['tasks', 'executions', from, to, type, statuses.join(',')],
    queryFn: () => tasksApi.executions({ from, to, type, statuses }),
    enabled: statuses.length > 0,
    // D33: só com rodada em execução; fora disso, o botão Atualizar
    refetchInterval: (q) => (q.state.data?.some(e => e.status === 'RUNNING') ? 5000 : false),
  });
  const tasks = useQuery({ queryKey: ['tasks', 'list'], queryFn: tasksApi.list });

  const typeOptions = useMemo(() => [...new Set((tasks.data ?? []).map(t => t.type))].sort(), [tasks.data]);
  // A API manda vigência decrescente; os Agendamentos invertem. É também o desempate quando se ordena por outra coluna.
  const rows = useMemo<ExecutionRow[]>(() => {
    const list = (statuses.length > 0 ? executions.data ?? [] : []).map(e => ({ ...e, resume: e.errorMessage ?? e.summary }));
    return scheduled ? list.reverse() : list;
  }, [executions.data, statuses.length, scheduled]);
  const { sortedData, requestSort, getSortIndicator, getSortClass, sortConfig } = useSortable(rows, {
    key: 'scheduledFor', direction: scheduled ? 'asc' : 'desc',
  });
  useEffect(() => setCurrentPage(1), [from, to, type, statuses, sortConfig]);
  const totalPages = Math.max(1, Math.ceil(sortedData.length / itemsPerPage));
  const paginated = sortedData.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);
  const columns = scheduled ? 9 : 10;

  const th = (key: string, label: string, extra = '') => (
    <th className={clsx('px-4 py-3 cursor-pointer select-none whitespace-nowrap', extra, getSortClass(key))}
        onClick={() => requestSort(key)}>
      {label}{getSortIndicator(key)}
    </th>
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap gap-3 items-end">
        <div>
          <label className="block text-xs text-gray-400 mb-1">De</label>
          <input type="date" className={selectClass} value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Até</label>
          <input type="date" className={selectClass} value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Tipo</label>
          <select className={selectClass} value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">Todos</option>
            {typeOptions.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        <Button type="button" variant="outline" onClick={() => executions.refetch()} isLoading={executions.isFetching}
                disabled={statuses.length === 0} className="ml-auto">
          <RefreshCw className="w-4 h-4 mr-1.5" />Atualizar
        </Button>
      </div>

      <Card>
        {/* <div className="mb-4 flex items-center gap-2">
          <h3 className="text-lg font-medium text-white">{scheduled ? 'Agendamentos' : 'Execuções'}</h3>
          <InfoTooltip content={scheduled
            ? 'Execuções agendadas de agora até o fim do período, calculadas do horário de cada tarefa ligada.'
            : 'Execuções que começaram no período, filtradas pelos status marcados. SCHEDULED inclui as agendadas de agora até o fim do período (as mesmas da aba Agendamentos).'} />
        </div>*/}
        {!scheduled && (
          <div className="pb-4">
            <StatusFlags selected={selected} onToggle={toggleStatus} />
          </div>
        )}
        {executions.isLoading && statuses.length > 0 ? (
          <div className="flex justify-center py-10"><LoadingSpinner className="h-6 w-6 text-accent" /></div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-xs text-gray-400 uppercase border-b border-white/10">
                <tr>
                  {th('id', '#')}
                  {th('taskName', 'TAREFA')}
                  {scheduled && th('taskName', 'TIPO')}
                  {th('scheduledFor', 'VIGÊNCIA')}
                  {!scheduled && th('startedAt', 'INÍCIO')}
                  {!scheduled && th('finishedAt', 'FIM')}
                  {!scheduled && th('durationSeconds', 'DURAÇÃO', 'text-right')}
                  {th('triggerType', 'ORIGEM')}
                  {!scheduled && th('requestedBy', 'PEDIDO POR')}
                  {!scheduled && th('resume', 'RESUMO')}
                  {!scheduled && th('status', 'STATUS')}
                  <th className="px-4 py-3 text-center">AÇÕES</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {paginated.map((e, i) => (
                  <tr key={e.id ?? `s-${e.taskId}-${e.scheduledFor}-${i}`} className="hover:bg-white/5 transition-colors">
                    <td className="px-4 py-3">{e.taskId}</td>
                    <td className="px-4 py-3">
                      <div className="text-white font-medium">{e.taskName}</div>
                      {e.type !== e.taskName && <div className="text-xs text-gray-500">{e.type}</div>}
                    </td>
                    {scheduled && (<td className="px-4 py-3">{e.type}</td>)}
                    <td className="px-4 py-3 whitespace-nowrap">{formatDateTime(e.scheduledFor)}</td>
                    {!scheduled && (<td className="px-4 py-3 whitespace-nowrap">{formatDateTime(e.startedAt, true)}</td>)}
                    {!scheduled && (<td className="px-4 py-3 whitespace-nowrap">{formatDateTime(e.finishedAt, true)}</td>)}
                    {!scheduled && (<td className="px-4 py-3 text-right whitespace-nowrap">{formatDuration(e.durationSeconds)}</td>)}
                    <td className="px-4 py-3">
                      <span className="cursor-help text-gray-300" title={TRIGGER_HINT[e.triggerType]}>{e.triggerType}</span>
                    </td>
                    {!scheduled && (<td className="px-4 py-3 text-gray-300">{e.id ? (e.requestedBy ?? 'Sistema') : ''}</td>)}
                    {!scheduled && (<td className="px-4 py-3 max-w-xs">
                      <span className="block truncate text-gray-300" title={e.resume}>{e.resume}</span>
                    </td>)}
                    {!scheduled && (
                      <td className="px-4 py-3">
                        <StatusBadge status={e.status} />
                        {e.stopRequestedAt && e.status === 'RUNNING' && (
                          <div className="text-xs text-orange-300 mt-1">Stop pedido</div>
                        )}
                      </td>
                    )}
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-center gap-1">
                        <button type="button" className={TABLE_ACTION_CLASS} title="Run: executar agora"
                                onClick={() => setConfirm({ id: e.taskId, name: e.taskName })}>
                          <Play className="w-4 h-4" />
                        </button>
                        <button type="button" className={TABLE_ACTION_CLASS}
                                title={e.stoppable ? 'Stop: parar no próximo ponto seguro' : 'Stop: só com a rodada em execução, nas tarefas com ponto de parada'}
                                disabled={!e.stoppable || !!e.stopRequestedAt || stop.isPending}
                                onClick={() => stop.mutate(e)}>
                          <Square className="w-4 h-4" />
                        </button>
                        <button type="button" className={TABLE_ACTION_CLASS} title="Ver log da rodada"
                                disabled={!e.id} onClick={() => e.id && onOpenExecution(e.id)}>
                          <FileText className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {sortedData.length === 0 && (
                  <tr>
                    <td colSpan={columns} className="px-4 py-8 text-center text-gray-500">
                      {statuses.length === 0 ? 'Marque ao menos um status.'
                        : scheduled ? 'Nenhuma execução agendada no período.' : 'Nenhuma rodada no período.'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
        {sortedData.length > itemsPerPage && (
          <div className="mt-4">
            <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage}
                        totalItems={sortedData.length} itemsPerPage={itemsPerPage}
                        onItemsPerPageChange={(n) => { setItemsPerPage(n); setCurrentPage(1); }} />
          </div>
        )}
      </Card>

      <RunConfirm
        task={confirm ? { ...confirm, attributes: tasks.data?.find(t => t.id === confirm.id)?.attributes } : null}
        pending={run.isPending}
        onClose={() => setConfirm(null)}
        onConfirm={() => { if (confirm) run.mutate(confirm, { onSettled: () => setConfirm(null) }); }}
      />
    </div>
  );
};

// ------------------------------------------------------------------------------------------------ Configuração

const ConfigurationTab = ({ onOpenExecution }: { onOpenExecution: (id: number) => void }) => {
  const [editing, setEditing] = useState<TaskItem | null>(null);
  const [confirm, setConfirm] = useState<TaskItem | null>(null);
  const { run } = useTaskActions();
  const tasks = useQuery({
    queryKey: ['tasks', 'list'],
    queryFn: tasksApi.list,
    refetchInterval: (q) => (q.state.data?.some(t => t.running) ? 5000 : false),
  });

  return (
    <div className="space-y-6">
      <Card
        title="Tarefas"
        titleInfo="Cada tarefa é uma instância de um tipo (a rotina): nome, horário, ligada/desligada e os seus atributos. Excluir uma tarefa não apaga o tipo."
        action={
          <div className="flex items-center gap-2">
            <Button type="button" variant="outline" size="sm" onClick={() => tasks.refetch()} isLoading={tasks.isFetching}>
              <RefreshCw className="w-4 h-4 mr-1.5" />Atualizar
            </Button>
            <span title={SOON}>
              <Button type="button" size="sm" disabled>
                <Plus className="w-4 h-4 mr-1.5" />Nova tarefa
              </Button>
            </span>
          </div>
        }
      >
        {tasks.isLoading ? (
          <div className="flex justify-center py-10"><LoadingSpinner className="h-6 w-6 text-accent" /></div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-xs text-gray-400 uppercase border-b border-white/10">
                <tr>
                  <th className="px-4 py-3">TAREFA</th>
                  <th className="px-4 py-3">TIPO</th>
                  <th className="px-4 py-3">SERVIÇO</th>
                  <th className="px-4 py-3">HORÁRIO</th>
                  <th className="px-4 py-3">PRÓXIMA</th>
                  <th className="px-4 py-3">SITUAÇÃO</th>
                  <th className="px-4 py-3">ÚLTIMA RODADA</th>
                  <th className="px-4 py-3 text-center">AÇÕES</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {(tasks.data ?? []).map(t => (
                  <tr key={t.id} className="hover:bg-white/5 transition-colors">
                    <td className="px-4 py-3 max-w-md">
                      <div className="text-white font-medium">{t.name}</div>
                      <div className="text-xs text-gray-500 line-clamp-2" title={t.effectiveDescription}>{t.effectiveDescription}</div>
                    </td>
                    <td className="px-4 py-3 text-gray-300">{t.type}</td>
                    <td className="px-4 py-3 text-gray-300">{t.service === 'APP_API' ? 'app-api' : 'adm-api'}</td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className={clsx(t.cron ? 'text-gray-200' : 'text-gray-500')} title={t.cron}>{describeCron(t.cron)}</span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-gray-300">{formatDateTime(t.nextRun)}</td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {!t.typeEnabled
                        ? <span className="text-danger" title="O tipo está desligado (muda só por migration).">Tipo desligado</span>
                        : t.enabled
                          ? <span className="text-success">Ligada</span>
                          : <span className="text-gray-500" title="Não roda pelo horário; o Run continua disponível.">Desligada</span>}
                      {t.runOnStartup && (
                        <div className="text-xs text-gray-400" title="Na subida do serviço, roda uma vez se ainda não rodou no dia.">
                          roda na subida
                        </div>
                      )}
                      {t.running && <div className="text-xs text-blue-300">executando</div>}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {t.lastRun ? (
                        <button type="button" className="flex items-center gap-2 hover:opacity-80"
                                onClick={() => onOpenExecution(t.lastRun!.id)} title="Ver log da rodada">
                          <StatusBadge status={t.lastRun.status} />
                          <span className="text-xs text-gray-400">{formatDateTime(t.lastRun.startedAt)}</span>
                        </button>
                      ) : <span className="text-gray-500">—</span>}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-center gap-1">
                        <button type="button" className={TABLE_ACTION_CLASS} title="Run: executar agora"
                                disabled={!t.typeEnabled} onClick={() => setConfirm(t)}>
                          <Play className="w-4 h-4" />
                        </button>
                        <button type="button" className={TABLE_ACTION_CLASS} title="Editar" onClick={() => setEditing(t)}>
                          <Pencil className="w-4 h-4" />
                        </button>
                        <span title={SOON}>
                          <button type="button" className={TABLE_ACTION_CLASS} disabled>
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </span>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <TaskEditModal task={editing} onClose={() => setEditing(null)} />
      <RunConfirm
        task={confirm}
        pending={run.isPending}
        onClose={() => setConfirm(null)}
        onConfirm={() => { if (confirm) run.mutate(confirm, { onSettled: () => setConfirm(null) }); }}
      />
    </div>
  );
};
