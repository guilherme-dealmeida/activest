import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Save, X } from 'lucide-react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Switch } from '@/components/ui/Switch';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';
import { tasksApi } from '@/api/tasks';
import { TaskAttributeItem, TaskItem } from '@/types';
import { buildCron, CronForm, CronMode, describeCron, parseCron, WEEK_DAYS } from '@/utils/cron';

const fieldClass =
  'flex h-10 w-full rounded-md border border-surface-light bg-surface px-3 py-2 text-sm text-white placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:ring-accent focus:border-transparent';

const MODE_LABELS: Record<CronMode, string> = {
  none: 'Sem horário (só pelo Run)',
  daily: 'Todo dia',
  weekly: 'Dias da semana',
  monthly: 'Uma vez por mês',
  advanced: 'Avançado (cron)',
};

const CRON_HELP =
  'Cron do Spring, 6 campos: segundo minuto hora dia-do-mês mês dia-da-semana, no fuso de São Paulo. Ex.: "0 30 8,21 * * *" = todo dia às 08:30 e às 21:30.';

/**
 * Editar uma tarefa (aba Configuração, docs/planos/PLANO_TAREFAS.md): nome (D17), descrição (vazia = a do tipo, O1),
 * ligada (D11), horário pelo editor (D10) e os atributos com o campo pelo tipo do valor (O6).
 */
export const TaskEditModal = ({ task, onClose }: { task: TaskItem | null; onClose: () => void }) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [runOnStartup, setRunOnStartup] = useState(false);
  const [cron, setCron] = useState<CronForm>(parseCron(null));
  const [values, setValues] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!task) return;
    setName(task.name);
    setDescription(task.description ?? '');
    setEnabled(task.enabled);
    setRunOnStartup(task.runOnStartup);
    setCron(parseCron(task.cron));
    setValues(Object.fromEntries(task.attributes.map(a => [a.name, a.value ?? ''])));
    setError(null);
  }, [task]);

  const save = useMutation({
    mutationFn: (body: Parameters<typeof tasksApi.update>[1]) => tasksApi.update((task as TaskItem).id, body),
    onSuccess: (saved) => {
      showToast(`Tarefa ${saved.name} salva.`, 'success');
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      onClose();
    },
    onError: (e) => showToast(apiMessage(e) ?? 'Não foi possível salvar a tarefa.', 'error', 8000),
  });

  if (!task) return null;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const built = buildCron(cron);
    if ('error' in built) {
      setError(built.error);
      return;
    }
    setError(null);
    save.mutate({
      name: name.trim().toUpperCase(),
      description: description.trim(),
      cron: built.cron,
      enabled,
      runOnStartup,
      attributes: values,
    });
  };

  const setMode = (mode: CronMode) => setCron(c => ({
    ...c,
    mode,
    raw: mode === 'advanced' && !c.raw ? (buildCron(c) as { cron?: string }).cron ?? '' : c.raw,
  }));

  const toggleDay = (key: string) => setCron(c => ({
    ...c,
    days: c.days.includes(key) ? c.days.filter(d => d !== key) : [...c.days, key],
  }));

  const preview = buildCron(cron);

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={`Editar tarefa — ${task.type}`}
      titleInfo={task.typeDescription}
      size="xl"
      footer={
        <div className="flex justify-end gap-3">
          <Button type="button" variant="outline" onClick={onClose}>
            <X className="w-4 h-4 mr-1.5" />Cancelar
          </Button>
          <Button type="submit" form="task-edit-form" isLoading={save.isPending}>
            <Save className="w-4 h-4 mr-1.5" />Salvar
          </Button>
        </div>
      }
    >
      <form id="task-edit-form" onSubmit={submit} className="space-y-5">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Input
            label="Nome"
            value={name}
            onChange={(e) => setName(e.target.value.toUpperCase().replace(/\s/g, '_'))}
            info="Maiúsculo, sem acento, sem espaço e sem ç; palavras separadas por _ (ex.: B3_DAILY_QUOTES_RELOAD)."
            maxLength={80}
            required
          />
          <div className="flex items-end pb-2">
            <Switch
              checked={enabled}
              onChange={setEnabled}
              label="Ligada"
              title="Desligada, a tarefa não roda pelo horário; o Run continua disponível."
            />
            <span className="ml-3 text-sm text-gray-300">{enabled ? 'Ligada' : 'Desligada'}</span>
            {!task.typeEnabled && (
              <span className="ml-3 text-xs text-danger" title="O tipo está desligado (muda só por migration).">
                tipo desligado
              </span>
            )}
            <span className="ml-6">
              <Switch
                checked={runOnStartup}
                onChange={setRunOnStartup}
                label="Roda na subida"
                title="Na subida do serviço, roda uma vez se ainda não rodou no dia (e se a rotina disser que há o que recuperar)."
              />
            </span>
            <span className="ml-3 text-sm text-gray-300">Roda na subida</span>
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-300 mb-1.5">
            <LabelWithInfo info="Vazia, vale a descrição do tipo.">Descrição</LabelWithInfo>
          </label>
          <textarea
            className={`${fieldClass} h-20 resize-y`}
            value={description}
            placeholder={task.typeDescription}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>

        <fieldset className="rounded-md border border-white/10 p-4 space-y-3">
          <legend className="px-1 text-sm font-medium text-gray-300">
            <LabelWithInfo info={CRON_HELP}>Horário</LabelWithInfo>
          </legend>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs text-gray-400 mb-1">Repetição</label>
              <select className={fieldClass} value={cron.mode} onChange={(e) => setMode(e.target.value as CronMode)}>
                {(Object.keys(MODE_LABELS) as CronMode[]).map(m => <option key={m} value={m}>{MODE_LABELS[m]}</option>)}
              </select>
            </div>
            {(cron.mode === 'daily' || cron.mode === 'weekly' || cron.mode === 'monthly') && (
              <div>
                <label className="block text-xs text-gray-400 mb-1">
                  {cron.mode === 'daily' ? 'Horário(s) — HH:MM, separados por vírgula' : 'Horário — HH:MM'}
                </label>
                <input className={fieldClass} value={cron.times} placeholder="19:00"
                       onChange={(e) => setCron(c => ({ ...c, times: e.target.value }))} />
              </div>
            )}
            {cron.mode === 'monthly' && (
              <div>
                <label className="block text-xs text-gray-400 mb-1">Dia do mês</label>
                <input type="number" min={1} max={31} className={fieldClass} value={cron.dayOfMonth}
                       onChange={(e) => setCron(c => ({ ...c, dayOfMonth: Number(e.target.value) }))} />
              </div>
            )}
            {cron.mode === 'advanced' && (
              <div>
                <label className="block text-xs text-gray-400 mb-1">Cron (6 campos)</label>
                <input className={`${fieldClass} font-mono`} value={cron.raw} placeholder="0 30 8,21 * * *"
                       onChange={(e) => setCron(c => ({ ...c, raw: e.target.value }))} />
              </div>
            )}
          </div>
          {cron.mode === 'weekly' && (
            <div className="flex flex-wrap gap-2">
              {WEEK_DAYS.map(d => (
                <label key={d.key} className="flex items-center gap-1.5 text-sm text-gray-300 cursor-pointer">
                  <input type="checkbox" checked={cron.days.includes(d.key)} onChange={() => toggleDay(d.key)}
                         className="accent-[#c9a84c]" />
                  {d.label}
                </label>
              ))}
            </div>
          )}
          <p className="text-xs text-gray-500">
            {'cron' in preview
              ? <>Fica: <span className="text-gray-300">{describeCron(preview.cron || null)}</span>
                  {preview.cron && <span className="font-mono ml-2" title={CRON_HELP}>({preview.cron})</span>}</>
              : <span className="text-danger">{preview.error}</span>}
          </p>
        </fieldset>

        {task.attributes.length > 0 && (
          <fieldset className="rounded-md border border-white/10 p-4">
            <legend className="px-1 text-sm font-medium text-gray-300">Atributos</legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {task.attributes.map(a => (
                <AttributeField key={a.typeAttributeId} attribute={a} value={values[a.name] ?? ''}
                                onChange={(v) => setValues(prev => ({ ...prev, [a.name]: v }))} />
              ))}
            </div>
          </fieldset>
        )}

        {error && <p className="text-sm text-danger">{error}</p>}
      </form>
    </Modal>
  );
};

/** Campo do atributo pelo tipo do valor (O6): ano, data, sim/não, número, texto (várias linhas) e lista. */
const AttributeField = ({ attribute, value, onChange }: {
  attribute: TaskAttributeItem; value: string; onChange: (v: string) => void;
}) => {
  const label = (
    <label className="block text-xs text-gray-400 mb-1">
      <LabelWithInfo info={
        <>
          {attribute.description}
          {attribute.defaultValue != null && <><br />Padrão do tipo: {attribute.defaultValue}</>}
        </>
      }>
        <span className="font-mono">{attribute.name}</span>
        {attribute.required && <span className="text-danger ml-0.5">*</span>}
      </LabelWithInfo>
    </label>
  );
  switch (attribute.valueType) {
    case 'BOOLEAN':
      return (
        <div>
          {label}
          <select className={fieldClass} value={value} onChange={(e) => onChange(e.target.value)}>
            {!attribute.required && <option value="">—</option>}
            <option value="true">Sim</option>
            <option value="false">Não</option>
          </select>
        </div>
      );
    case 'DATE':
      return (
        <div>
          {label}
          <input type="date" className={fieldClass} value={value} onChange={(e) => onChange(e.target.value)} />
        </div>
      );
    case 'YEAR':
    case 'INTEGER':
      return (
        <div>
          {label}
          <input inputMode="numeric" className={fieldClass} value={value}
                 placeholder={attribute.valueType === 'YEAR' ? 'aaaa' : ''}
                 onChange={(e) => onChange(e.target.value.replace(/[^\d-]/g, ''))} />
        </div>
      );
    case 'TEXT':
      if (attribute.name !== 'LINES') {
        return (
          <div>
            {label}
            <input className={fieldClass} value={value} onChange={(e) => onChange(e.target.value)} />
          </div>
        );
      }
      return (
        <div className="md:col-span-2">
          {label}
          <textarea className={`${fieldClass} h-24 resize-y font-mono`} value={value}
                    onChange={(e) => onChange(e.target.value)} />
        </div>
      );
    default:
      return (
        <div>
          {label}
          <input className={fieldClass} value={value} placeholder="A, B, C" onChange={(e) => onChange(e.target.value)} />
        </div>
      );
  }
};
