import React, { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Save, X } from 'lucide-react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Switch } from '@/components/ui/Switch';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { clsx } from 'clsx';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';
import { goalsApi } from '@/api/goals';
import { Goal, GoalAssumption, GoalCatalog, GoalField, GoalRequest, GoalScopeValue, GoalTypeInfo } from '@/types/goals';
import { GoalFieldInput } from './GoalFieldInput';
import { FieldLabel } from './FieldLabel';
import { MultiPicker } from './MultiPicker';
import { formatGoalValue, SITUATION } from './goalFormat';
import { Badge } from '@/components/ui/Badge';
import { assetText } from '@/utils/assetLabel';
import { barWidth } from '@/utils/goalProgress';

const SectionTitle = ({ children }: { children: React.ReactNode }) => (
  <p className="text-xs uppercase tracking-wide text-gray-500">{children}</p>
);

/** Classes literais (o Tailwind só gera o que aparece escrito no código). */
const COLUMNS: Record<number, string> = { 2: 'lg:grid-cols-2', 3: 'lg:grid-cols-3', 4: 'lg:grid-cols-4' };

const RESERVATION = [['WITH', 'Com'], ['WITHOUT', 'Sem'], ['ONLY', 'Só ela']] as const;

const ASSUMPTIONS = ['returnRate', 'returnSource', 'monthlyContribution', 'inflation'];
/** Premissas com valor automático: o campo já vem com ele; editar fixa o valor, apagar volta ao automático. */
const AUTOMATIC = new Set(['returnRate', 'monthlyContribution', 'inflation']);
const DIMENSION_FIELDS = new Set(['item']);

interface Props {
  isOpen: boolean;
  onClose: () => void;
  catalog: GoalCatalog;
  /** Edição: o tipo não muda (trocar de tipo = meta nova). */
  goal?: Goal | null;
  onSaved?: (goal: Goal) => void;
}

type Params = Record<string, unknown>;

const visible = (f: GoalField, params: Params): boolean => {
  if (!f.requiredIf) return true;
  const [key, values] = f.requiredIf.split('=');
  const v = params[key];
  return v !== undefined && v !== null && values.split(',').includes(String(v));
};

const defaultsOf = (t: GoalTypeInfo): Params => {
  const p: Params = {};
  for (const f of t.fields) {
    if (f.key === 'targetValue' || f.key === 'targetDate') continue;
    if (f.defaultValue !== null && f.defaultValue !== undefined) p[f.key] = f.defaultValue;
  }
  return p;
};

/**
 * Nova meta / editar (docs/planos/PLANO_METAS.md, Anexo A 12): 1) categoria → 2) tipo, com a explicação e o que o
 * Activest calcula → 3) formulário gerado do catálogo, com o valor atual calculado ao vivo (preview). Nenhum campo
 * pede o que já está na carteira (G1).
 */
export const GoalFormModal = ({ isOpen, onClose, catalog, goal, onSaved }: Props) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [step, setStep] = useState<'category' | 'type' | 'form'>(goal ? 'form' : 'category');
  const [category, setCategory] = useState<string | null>(goal?.category ?? null);
  const [typeKey, setTypeKey] = useState<string | null>(goal?.type ?? null);
  const [name, setName] = useState(goal?.name ?? '');
  const [targetValue, setTargetValue] = useState<number | null>(goal?.targetValue ?? null);
  const [targetDate, setTargetDate] = useState<string | null>(goal?.targetDate ?? null);
  const [params, setParams] = useState<Params>(goal?.params ?? {});
  const [scopeKind, setScopeKind] = useState<string>(goal?.scope?.kind ?? 'ALL');
  const [scopeValues, setScopeValues] = useState<string[]>(goal?.scope?.values ?? []);
  const [reservation, setReservation] = useState<GoalScopeValue['reservation']>(goal?.scope?.reservation ?? null);
  const [excludeTypes, setExcludeTypes] = useState<string[]>(goal?.scope?.excludeTypes ?? []);
  const [notifications, setNotifications] = useState<boolean>(goal?.notifications ?? true);
  // Observações saíram da tela (PENDENCIAS 37): na edição, a que já existe é mantida
  const notes = goal?.notes ?? '';

  const type = useMemo(() => catalog.types.find(t => t.type === typeKey) ?? null, [catalog, typeKey]);
  const { data: scopeOptions } = useQuery({ queryKey: ['goals', 'scope-options'], queryFn: goalsApi.scopeOptions, enabled: isOpen });

  useEffect(() => {
    if (type && !goal) {
      setParams(defaultsOf(type));
      setTargetValue(null);
      setTargetDate(null);
      setScopeKind('ALL');
      setScopeValues([]);
      setReservation(type.reservation === 'EXPLICIT' ? null : type.reservation === 'DEFAULT_WITHOUT' ? 'WITHOUT' : type.reservation === 'NONE' ? null : 'WITH');
      setExcludeTypes([]);
      setName(type.label);
    }
  }, [type, goal]);

  const request: GoalRequest | null = type ? {
    name,
    type: type.type,
    targetValue,
    targetDate,
    params: Object.fromEntries(Object.entries(params).filter(([, v]) => v !== null && v !== '' && !(Array.isArray(v) && v.length === 0))),
    scope: type.reservation === 'NONE' ? {} : { kind: scopeKind, values: scopeValues, reservation, excludeTypes },
    notifications,
    notes: notes || null,
  } : null;

  // Preview ao vivo, 500 ms depois de parar de mexer
  const [debounced, setDebounced] = useState<GoalRequest | null>(null);
  const key = JSON.stringify(request);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(request), 500);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  const preview = useQuery({
    queryKey: ['goals', 'preview', JSON.stringify(debounced)],
    queryFn: () => goalsApi.preview(debounced as GoalRequest),
    enabled: isOpen && step === 'form' && debounced !== null,
    retry: false,
  });

  const save = useMutation({
    mutationFn: (req: GoalRequest) => (goal ? goalsApi.update(goal.id, req) : goalsApi.create(req)),
    onSuccess: saved => {
      queryClient.invalidateQueries({ queryKey: ['goals'] });
      showToast(goal ? 'Meta atualizada' : saved.lifecycle === 'CONCLUIDA' ? 'Meta criada — e já atingida' : 'Meta criada', 'success');
      onSaved?.(saved);
      onClose();
    },
    onError: e => showToast(apiMessage(e) ?? 'Não foi possível salvar a meta', 'error'),
  });

  const fields = type?.fields ?? [];
  const main = fields.filter(f => !ASSUMPTIONS.includes(f.key) && visible(f, params)
    && !(f.key === 'to' && params.period !== 'CUSTOM'));
  const isRequired = (f: GoalField) => f.required || !!f.requiredIf;
  const assumptions = fields.filter(f => ASSUMPTIONS.includes(f.key));
  const dimension = params.dimension as string | undefined;
  const fieldValue = (f: GoalField) => (f.key === 'targetValue' ? targetValue : f.key === 'targetDate' ? targetDate : params[f.key]);
  const setField = (f: GoalField, v: unknown) => {
    if (f.key === 'targetValue') setTargetValue(v as number | null);
    else if (f.key === 'targetDate') setTargetDate(v as string | null);
    else setParams(p => ({ ...p, [f.key]: v }));
  };
  const optionsFor = (f: GoalField) => {
    if (DIMENSION_FIELDS.has(f.key) && dimension) return scopeOptions?.options[dimension];
    return undefined;
  };
  const kindOptions = scopeOptions?.options[scopeKind] ?? [];
  const ev = preview.data?.evaluation;

  // Valores automáticos das premissas: vêm já no campo, antes mesmo de o cadastro estar completo (o preview exige
  // alvo e prazo válidos). Dependem só do tipo, do escopo e da base (custo ou mercado).
  const autoKey = debounced ? [debounced.type, JSON.stringify(debounced.scope), debounced.params?.returnSource,
    debounced.params?.basis, debounced.params?.assetId] : null;
  const autoQuery = useQuery({
    queryKey: ['goals', 'assumptions', JSON.stringify(autoKey)],
    queryFn: () => goalsApi.assumptions(debounced as GoalRequest),
    enabled: isOpen && step === 'form' && debounced !== null && assumptions.length > 0,
    retry: false,
    placeholderData: prev => prev,
  });
  const autos: Record<string, GoalAssumption> = Object.fromEntries((autoQuery.data ?? []).map(a => [a.key, a]));
  const informed = (key: string) => params[key] !== null && params[key] !== undefined && params[key] !== '';
  // meta pelo custo: a projeção não usa rendimento (o campo não se aplica)
  const noReturn = !informed('returnRate') && autos.returnRate?.reference?.startsWith('Sem rendimento');
  const shownAssumptions = assumptions.filter(f =>
    !(noReturn && (f.key === 'returnRate' || f.key === 'returnSource'))
    && !(f.key === 'returnSource' && informed('returnRate')));

  const footer = step === 'form' ? (
    <div className="flex flex-wrap items-center justify-between gap-3">
      {!goal ? <Button variant="ghost" onClick={() => setStep('type')}><ArrowLeft className="w-4 h-4 mr-1" />Voltar</Button> : <span />}
      <div className="flex flex-wrap items-center gap-4">
        <label className="flex items-center gap-2 text-sm text-gray-300">
          <Switch checked={notifications} onChange={setNotifications} label="Avisos da meta" />
          Avisar em Notificações
        </label>
        <Button variant="outline" onClick={onClose}><X className="w-4 h-4 mr-1" />Cancelar</Button>
        <Button onClick={() => request && save.mutate(request)} isLoading={save.isPending}><Save className="w-4 h-4 mr-1" />Salvar</Button>
      </div>
    </div>
  ) : undefined;

  const title = (goal ? 'Editar meta' : 'Nova meta')
    + (step === 'category' ? ' - Categorias' : step === 'type' ? ' - Tipos' : type ? ` · ${type.label}` : '')

  return (
    <Modal isOpen={isOpen} onClose={onClose} size="2xl" footer={footer} title={title}
      description={step === 'form' && type ? type.description : undefined}
      titleInfo="Você define o objetivo; o Activest calcula quanto você já tem, o progresso e a projeção a partir da carteira.">

      {step === 'category' && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          {catalog.categories.map(c => (
            <button key={c.value} type="button" onClick={() => { setCategory(c.value); setStep('type'); }}
              className="text-left rounded-lg border border-surface-light p-4 hover:border-accent/60 hover:bg-surface-light transition-colors">
              <p className="text-white font-medium">{c.label}</p>
              <p className="text-xs text-gray-400 mt-1">
                {catalog.types.filter(t => t.category === c.value).map(t => t.label).join(' · ')}
              </p>
            </button>
          ))}
        </div>
      )}

      {step === 'type' && (
        <div className="space-y-3">
          <Button variant="ghost" size="sm" onClick={() => setStep('category')}><ArrowLeft className="w-4 h-4 mr-1" />Categorias</Button>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
            {catalog.types.filter(t => t.category === category).map(t => (
              <button key={t.type} type="button" onClick={() => { setTypeKey(t.type); setStep('form'); }}
                className="text-left rounded-lg border border-surface-light p-4 hover:border-accent/60 hover:bg-surface-light transition-colors">
                <p className="text-white font-medium">{t.label}</p>
                <p className="text-sm text-gray-300 mt-1">{t.description}</p>
                <ul className="mt-2 space-y-0.5 text-xs text-gray-400 list-disc pl-4">
                  {t.calculates.map(c => <li key={c}>{c}</li>)}
                </ul>
                {t.limitations.length > 0 && <p className="text-xs text-gray-500 mt-2">{t.limitations.join(' · ')}</p>}
              </button>
            ))}
          </div>
        </div>
      )}

      {step === 'form' && type && (
        <div className={clsx('grid grid-cols-1 gap-6',
          COLUMNS[2 + (type.reservation !== 'NONE' ? 1 : 0) + (assumptions.length > 0 ? 1 : 0)])}>
          <div className="space-y-4">
            <SectionTitle>Objetivo</SectionTitle>
            <div>
              <FieldLabel required>Nome</FieldLabel>
              <Input value={name} maxLength={120} onChange={e => setName(e.target.value)} />
            </div>
            {main.map(f => (
              <GoalFieldInput key={f.key} field={f} value={fieldValue(f)} onChange={v => setField(f, v)}
                options={optionsFor(f)} required={isRequired(f)}
                assetLabel={{ ticker: params.ticker as string | undefined, assetType: params.assetType as string | undefined }} />
            ))}
          </div>

          {type.reservation !== 'NONE' && <div className="space-y-4">
            <SectionTitle>O que a meta considera</SectionTitle>
            {(
              <>
                {type.scopes.length > 1 && (
                  <div>
                    <FieldLabel required>Escopo</FieldLabel>
                    <Select value={scopeKind} onChange={e => { setScopeKind(e.target.value); setScopeValues([]); }}>
                      {type.scopes.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
                    </Select>
                  </div>
                )}
                {scopeKind !== 'ALL' && (
                  <div>
                    <FieldLabel required>{type.scopes.find(s => s.value === scopeKind)?.label}</FieldLabel>
                    <MultiPicker options={kindOptions} value={scopeValues} onChange={setScopeValues} emptyText="Nenhum item na carteira" />
                  </div>
                )}
                <div>
                  <FieldLabel required={type.reservation === 'EXPLICIT'}
                    info="A reserva de emergência são as posições marcadas como reserva na Carteira.">Reserva de emergência</FieldLabel>
                  <div className="grid grid-cols-3 rounded-md border border-surface-light p-0.5">
                    {RESERVATION.map(([v, l]) => (
                      <button key={v} type="button" onClick={() => setReservation(v)}
                        className={clsx('rounded px-2 py-1.5 text-sm transition-colors',
                          reservation === v ? 'bg-accent text-primary font-medium' : 'text-gray-300 hover:bg-surface-light')}>
                        {l}
                      </button>
                    ))}
                  </div>
                </div>
                {(scopeOptions?.options.TYPE?.length ?? 0) > 1 && scopeKind !== 'TYPE' && (
                  <div>
                    <FieldLabel>Excluir tipos de ativo</FieldLabel>
                    <MultiPicker options={scopeOptions?.options.TYPE ?? []} value={excludeTypes} onChange={setExcludeTypes}
                      placeholder="Nenhum excluído" />
                  </div>
                )}
              </>
            )}
          </div>}

          {assumptions.length > 0 && (
            <div className="space-y-4">
              <SectionTitle>Premissas da projeção</SectionTitle>
              {shownAssumptions.map(f => {
                if (!AUTOMATIC.has(f.key)) {
                  return <GoalFieldInput key={f.key} field={f} value={params[f.key]} onChange={v => setField(f, v)} required={false} />;
                }
                const auto = autos[f.key];
                return (
                  <div key={f.key}>
                    <GoalFieldInput field={f} value={informed(f.key) ? params[f.key] : auto?.value ?? null}
                      onChange={v => setField(f, v)} required={false} />
                    <p className="mt-1 text-xs text-gray-500">
                      {informed(f.key) ? (
                        <>Informado por você ·{' '}
                          <button type="button" className="text-accent hover:underline"
                            onClick={() => setParams(p => ({ ...p, [f.key]: null }))}>usar automático</button></>
                      ) : auto ? `Automático · ${auto.reference ?? ''}` : 'Automático'}
                    </p>
                  </div>
                );
              })}
            </div>
          )}

          <div className="lg:sticky lg:top-0 h-fit space-y-3 rounded-lg bg-background/40 p-4">
            <SectionTitle>Calculado agora, a partir da carteira</SectionTitle>
            {preview.isFetching && <LoadingSpinner className="h-5 w-5 text-accent" />}
            {preview.isError && <p className="text-sm text-yellow-500">{apiMessage(preview.error) ?? 'Complete os campos para ver o cálculo.'}</p>}
            {ev && !preview.isError && (
              <>
                <p className="text-sm text-white">{ev.objective}</p>
                {preview.data?.scopeLabel && <p className="text-xs text-gray-400">{assetText(preview.data.scopeLabel)}</p>}
                <div>
                  <p className="text-xs text-gray-400">{ev.indicatorLabel}</p>
                  <p className="text-2xl font-semibold text-white">{formatGoalValue(ev.indicator, ev.unit)}</p>
                  {ev.target !== null && <p className="text-xs text-gray-400">Alvo: {formatGoalValue(ev.target, ev.unit)}</p>}
                </div>
                {ev.progressPct !== null && (
                  <div className="h-2 w-full rounded-full bg-surface-light overflow-hidden">
                    <div className="h-full bg-accent" style={{ width: `${barWidth(ev.progressPct)}%` }} />
                  </div>
                )}
                <Badge variant={SITUATION[ev.situation].variant}>{SITUATION[ev.situation].label}</Badge>
                {ev.situationNote && <p className="text-xs text-gray-400">{ev.situationNote}</p>}
                {ev.issues.length > 0 && (
                  <ul className="text-xs text-yellow-500 list-disc pl-4">{ev.issues.slice(0, 4).map(i => <li key={i}>{i}</li>)}</ul>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
};
