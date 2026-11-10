import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronDown, ChevronsDownUp, ChevronsUpDown, Plus, X } from 'lucide-react';
import { clsx } from 'clsx';
import { goalsApi } from '@/api/goals';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Select } from '@/components/ui/Select';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { GoalCard } from '@/components/goals/GoalCard';
import { GoalFormModal } from '@/components/goals/GoalFormModal';
import { SITUATION } from '@/components/goals/goalFormat';
import { Goal, GoalLifecycle, GoalSituation, GoalSummary } from '@/types/goals';
import { useAssetLabel } from '@/utils/assetLabel';

const TABS: { value: GoalLifecycle; label: string }[] = [
  { value: 'ATIVA', label: 'Ativas' },
  { value: 'PAUSADA', label: 'Pausadas' },
  { value: 'CONCLUIDA', label: 'Concluídas' },
  { value: 'ARQUIVADA', label: 'Arquivadas' },
];

/**
 * Ordem dentro de cada categoria: o que pede atenção primeiro — fora do objetivo, atrasada, em andamento, dentro do
 * objetivo (cada uma com os equivalentes), e por fim sem dados.
 */
const ORDER: GoalSituation[] = [
  'FORA_DO_OBJETIVO', 'PRAZO_VENCIDO', 'ATRASADA', 'EM_ANDAMENTO', 'ESTIMATIVA',
  'NO_RITMO', 'DENTRO_DO_OBJETIVO', 'ATINGIDA', 'SEM_DADOS',
];
const rank = (g: Goal) => {
  const i = g.evaluation ? ORDER.indexOf(g.evaluation.situation) : -1;
  return i < 0 ? ORDER.length : i;
};

/** Cards do resumo: o número filtra as metas da situação (e limpa a categoria); zero não faz nada. */
interface SummaryCard {
  title: string;
  count: (s: GoalSummary) => number;
  className?: string;
  /** Situações que o card filtra; vazio = todas da aba. */
  situations: GoalSituation[];
  tab: GoalLifecycle;
}

const CARDS: SummaryCard[] = [
  { title: 'Ativas', count: s => s.active, tab: 'ATIVA', situations: [] },
  { title: 'Fora do objetivo', count: s => s.outside, className: 'text-danger', tab: 'ATIVA', situations: ['FORA_DO_OBJETIVO'] },
  { title: 'Atrasadas / prazo vencido', count: s => s.behind, className: 'text-yellow-500', tab: 'ATIVA', situations: ['ATRASADA', 'PRAZO_VENCIDO'] },
  { title: 'Em andamento', count: s => s.inProgress, className: 'text-blue-500', tab: 'ATIVA', situations: ['EM_ANDAMENTO', 'ESTIMATIVA'] },
  { title: 'No ritmo / dentro', count: s => s.onTrack, className: 'text-success', tab: 'ATIVA', situations: ['NO_RITMO', 'DENTRO_DO_OBJETIVO'] },
  { title: 'Concluídas', count: s => s.completed, tab: 'CONCLUIDA', situations: [] },
];

/**
 * Etiquetas de situação (10/11/2026): "No ritmo" e "Dentro do objetivo" viram uma só, como no card do resumo; as
 * demais situações têm a sua. A chave da etiqueta é a primeira situação do grupo.
 */
const TAG_GROUPS: { situations: GoalSituation[]; label?: string }[] = [
  { situations: ['NO_RITMO', 'DENTRO_DO_OBJETIVO'], label: 'No ritmo / dentro' },
];
const situationsOfTag = (s: GoalSituation): GoalSituation[] =>
  TAG_GROUPS.find(g => g.situations.includes(s))?.situations ?? [s];

const TAG_COLORS: Record<string, string> = {
  success: 'border-success/40 text-success',
  danger: 'border-danger/40 text-danger',
  warning: 'border-yellow-500/40 text-yellow-500',
  info: 'border-blue-500/40 text-blue-500',
  default: 'border-surface-light text-gray-300',
};
const TAG_ACTIVE: Record<string, string> = {
  success: 'bg-success/20',
  danger: 'bg-danger/20',
  warning: 'bg-yellow-500/20',
  info: 'bg-blue-500/20',
  default: 'bg-surface-light',
};

/**
 * Metas (docs/planos/PLANO_METAS.md, Anexo A 12): resumo por situação (o número filtra), filtros por ciclo, categoria e
 * situação (etiquetas das metas cadastradas), e as metas agrupadas por categoria — dentro de cada uma, o que pede
 * atenção primeiro.
 */
export const GoalsPage = () => {
  // nomes curtos dos fundos (o ticker do fundo é o CNPJ — PLANO_FUNDOS_INVESTIMENTO.md, D3)
  useAssetLabel();
  const [tab, setTab] = useState<GoalLifecycle>('ATIVA');
  const [category, setCategory] = useState('');
  // situações filtradas; null = todas (padrão: todas as etiquetas marcadas — 10/11/2026)
  const [situations, setSituations] = useState<GoalSituation[] | null>(null);
  const [creating, setCreating] = useState(false);
  // sanfona por categoria: todas abertas por padrão
  const [closed, setClosed] = useState<string[]>([]);
  const toggleGroup = (c: string) => setClosed(prev => (prev.includes(c) ? prev.filter(x => x !== c) : [...prev, c]));
  const { data: catalog } = useQuery({ queryKey: ['goals', 'catalog'], queryFn: goalsApi.catalog, staleTime: Infinity });
  const { data, isLoading } = useQuery({ queryKey: ['goals', 'list', tab], queryFn: () => goalsApi.list(tab) });

  const changeTab = (t: GoalLifecycle) => { setTab(t); setSituations(null); };

  // etiquetas: as situações das metas da aba (na categoria escolhida), com a contagem
  const inCategory = useMemo(() => (data?.goals ?? []).filter(g => !category || g.category === category), [data, category]);
  const tags = useMemo(() => {
    const count = new Map<GoalSituation, number>();
    for (const g of inCategory) {
      if (g.evaluation) count.set(g.evaluation.situation, (count.get(g.evaluation.situation) ?? 0) + 1);
    }
    const result: { key: GoalSituation; situations: GoalSituation[]; label?: string; count: number }[] = [];
    for (const s of ORDER) {
      if (!count.has(s)) continue;
      const group = situationsOfTag(s);
      const existing = result.find(t => t.key === group[0]);
      if (existing) {
        existing.count += count.get(s) ?? 0;
      } else {
        result.push({ key: group[0], situations: group, label: TAG_GROUPS.find(g => g.situations === group)?.label,
          count: count.get(s) ?? 0 });
      }
    }
    return result;
  }, [inCategory]);

  const tagOn = (tag: { situations: GoalSituation[] }) =>
    situations === null || tag.situations.some(x => situations.includes(x));
  /** Marca/desmarca a etiqueta; tudo marcado volta a "todas" (null). */
  const toggle = (tag: { situations: GoalSituation[] }) => {
    const all = tags.flatMap(t => t.situations);
    const current = situations ?? all;
    const next = tagOn(tag)
      ? current.filter(x => !tag.situations.includes(x))
      : [...current, ...tag.situations];
    setSituations(all.every(x => next.includes(x)) ? null : next);
  };

  const groups = useMemo(() => {
    const goals = inCategory
      .filter(g => situations === null || (g.evaluation && situations.includes(g.evaluation.situation)))
      .sort((a, b) => rank(a) - rank(b) || a.name.localeCompare(b.name, 'pt-BR'));
    return (catalog?.categories ?? [])
      .map(c => ({ category: c, goals: goals.filter(g => g.category === c.value) }))
      .filter(gr => gr.goals.length > 0);
  }, [inCategory, situations, catalog]);

  const s = data?.summary;
  const pick = (card: SummaryCard) => {
    if (!s || card.count(s) === 0) return;
    setCategory('');
    setTab(card.tab);
    setSituations(card.situations.length === 0 ? null : card.situations);
  };
  const cardActive = (card: SummaryCard) => tab === card.tab && !category && (card.situations.length === 0
    ? situations === null
    : situations !== null && situations.length === card.situations.length
      && card.situations.every(x => situations.includes(x)));

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-gray-400">Você define o objetivo; o Activest calcula a situação, o progresso e a projeção a partir da carteira.</p>
        <Button onClick={() => setCreating(true)} disabled={!catalog}><Plus className="w-4 h-4 mr-2" />Nova meta</Button>
      </div>

      {s && (
        <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-4">
          {CARDS.map(card => {
            const n = card.count(s);
            return (
              <Card key={card.title} title={card.title}
                className={clsx(cardActive(card) && n > 0 && 'border-accent/60')}
                value={
                  <button type="button" onClick={() => pick(card)} disabled={n === 0}
                    title={n > 0 ? `Ver: ${card.title.toLowerCase()}` : undefined}
                    className={clsx(card.className, n > 0 ? 'hover:underline cursor-pointer' : 'cursor-default')}>
                    {n}
                  </button>
                } />
            );
          })}
        </div>
      )}

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex gap-1 border-b border-surface-light">
          {TABS.map(t => (
            <button key={t.value} type="button" onClick={() => changeTab(t.value)}
              className={clsx('px-4 py-2 text-sm border-b-2 -mb-px transition-colors',
                tab === t.value ? 'border-accent text-white' : 'border-transparent text-gray-400 hover:text-white')}>
              {t.label}
            </button>
          ))}
        </div>
        <div className="w-60">
          <Select value={category} onChange={e => setCategory(e.target.value)}>
            <option value="">Todas as categorias</option>
            {catalog?.categories.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
          </Select>
        </div>
      </div>

      {(tags.length > 0 || groups.length > 0) && (
        <div className="flex flex-wrap items-center gap-2">
          {tags.map(tag => {
            const { label: situationLabel, variant } = SITUATION[tag.key];
            const label = tag.label ?? situationLabel;
            const count = tag.count;
            const on = tagOn(tag);
            return (
              <button key={tag.key} type="button" onClick={() => toggle(tag)} aria-pressed={on}
                className={clsx('rounded-full border px-3 py-1 text-xs transition-colors', TAG_COLORS[variant],
                  on ? TAG_ACTIVE[variant] : 'opacity-70 hover:opacity-100')}>
                {label} <span className="ml-1 opacity-80">{count}</span>
              </button>
            );
          })}
          {situations !== null && (
            <button type="button" onClick={() => setSituations(null)}
              className="flex items-center gap-1 text-xs text-gray-400 hover:text-white">
              <X className="h-3 w-3" />Limpar
            </button>
          )}
          {/* Recolher / expandir todas as categorias (10/11/2026) */}
          {groups.length > 0 && (
            <div className="ml-auto flex items-center gap-3">
              <button type="button" onClick={() => setClosed(groups.map(gr => gr.category.value))}
                disabled={groups.every(gr => closed.includes(gr.category.value))}
                className="flex items-center gap-1 text-xs text-gray-400 hover:text-white disabled:opacity-40 disabled:hover:text-gray-400">
                <ChevronsDownUp className="h-3.5 w-3.5" />Recolher tudo
              </button>
              <button type="button" onClick={() => setClosed([])}
                disabled={!groups.some(gr => closed.includes(gr.category.value))}
                className="flex items-center gap-1 text-xs text-gray-400 hover:text-white disabled:opacity-40 disabled:hover:text-gray-400">
                <ChevronsUpDown className="h-3.5 w-3.5" />Expandir tudo
              </button>
            </div>
          )}
        </div>
      )}

      {isLoading ? (
        <div className="flex justify-center py-12"><LoadingSpinner className="h-8 w-8 text-accent" /></div>
      ) : groups.length === 0 ? (
        <Card>
          <p className="text-gray-400 text-sm text-center py-8">
            {tab === 'ATIVA' && !category && situations === null
              ? 'Nenhuma meta ativa. Crie a primeira: o Activest acompanha a partir da sua carteira.'
              : 'Nenhuma meta aqui.'}
          </p>
        </Card>
      ) : (
        <div className="space-y-6">
          {groups.map(gr => (
            <section key={gr.category.value} className="space-y-3">
              <h2>
                <button type="button" onClick={() => toggleGroup(gr.category.value)}
                  aria-expanded={!closed.includes(gr.category.value)}
                  className="flex w-full items-center gap-2 border-b border-surface-light pb-2 text-left text-sm font-medium text-gray-300 hover:text-white">
                  <ChevronDown className={clsx('h-4 w-4 transition-transform',
                    closed.includes(gr.category.value) && '-rotate-90')} aria-hidden="true" />
                  {gr.category.label} <span className="text-gray-500">· {gr.goals.length}</span>
                </button>
              </h2>
              {!closed.includes(gr.category.value) && (
                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                  {gr.goals.map(g => <GoalCard key={g.id} goal={g} />)}
                </div>
              )}
            </section>
          ))}
        </div>
      )}

      {creating && catalog && <GoalFormModal isOpen onClose={() => setCreating(false)} catalog={catalog} />}
    </div>
  );
};
