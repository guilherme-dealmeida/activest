import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Archive, ArchiveRestore, ArrowLeft, CheckCircle2, Pause, Pencil, Play, Trash2,
} from 'lucide-react';
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Line, ReferenceLine, ResponsiveContainer, ComposedChart, Tooltip, XAxis,
  YAxis,
} from 'recharts';
import { goalsApi, GoalAction } from '@/api/goals';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { useToast } from '@/context/ToastContext';
import { useTheme } from '@/context/ThemeContext';
import { apiMessage } from '@/utils/apiError';
import { getReturnColor } from '@/utils/format';
import { GoalFormModal } from '@/components/goals/GoalFormModal';
import { formatDateBR, formatGoalValue, LIFECYCLE, NATURE, QUALITY, SITUATION } from '@/components/goals/goalFormat';
import { GoalEvaluation, GoalFigure, GoalUnit } from '@/types/goals';
import { assetText, useAssetLabel } from '@/utils/assetLabel';
import { barWidth, limitBarClass, limitUsage } from '@/utils/goalProgress';

const EVENT_LABEL: Record<string, string> = {
  CRIADA: 'Criada', EDITADA: 'Editada', PAUSADA: 'Pausada', RETOMADA: 'Retomada', CONCLUIDA: 'Concluída',
  ARQUIVADA: 'Arquivada', DESARQUIVADA: 'Desarquivada', ATINGIDA: 'Atingiu o alvo', SAIU_DO_OBJETIVO: 'Saiu do objetivo',
  VOLTOU_AO_OBJETIVO: 'Voltou ao objetivo', PRAZO_VENCIDO: 'Prazo vencido',
};

const ISSUE_LABEL: Record<string, string> = {
  STALE_PRICE: 'Cotação defasada', NO_PRICE: 'Sem cotação', NO_CURVE: 'Título sem curva',
  BONUS_NO_COST: 'Bonificação sem custo', DIVIDEND_NO_DATE: 'Provento sem data', IMPORT_PENDING: 'Importação aguardando',
};

const FigureRow = ({ f }: { f: GoalFigure }) => (
  <div className="flex items-start justify-between gap-4 py-2 border-b border-surface-light last:border-0">
    <div>
      <p className="text-sm text-gray-300">{f.label}</p>
      {f.note && <p className="text-xs text-gray-500">{f.note}</p>}
    </div>
    <div className="text-right shrink-0">
      {f.value !== null && <p className={`text-sm font-medium ${NATURE[f.nature].className}`}>{formatGoalValue(f.value, f.unit)}</p>}
      <p className="text-[11px] text-gray-500">{NATURE[f.nature].label}</p>
    </div>
  </div>
);

/** Tabela genérica dos itens da avaliação (períodos, participações, pendências). */
/**
 * Aporte mensal / anual (APO-2): uma barra por período contra o valor-alvo (revisão de 25/10/2026) — verde cumprido,
 * vermelho não cumprido, dourado o período em andamento. Substitui a lista e o acumulado (que não se compara com um
 * valor por período).
 */
const PeriodBars = ({ items, target, tolerance, isDark }: {
  items: Record<string, unknown>[]; target: number; tolerance: number; isDark: boolean;
}) => {
  const grid = isDark ? '#2a2a2a' : '#e8e8e0';
  const data = items.map(i => ({
    period: String(i.period), amount: Number(i.amount ?? 0), closed: Boolean(i.closed), fulfilled: Boolean(i.fulfilled),
  }));
  const done = data.filter(d => d.closed && d.fulfilled).length;
  const closed = data.filter(d => d.closed).length;
  return (
    <>
      <p className="text-xs text-gray-500 mb-2">
        {closed > 0 ? `${done} de ${closed} períodos fechados cumpridos` : 'Nenhum período fechado ainda'}
        {' · '}<span className="text-success">■</span> cumprido <span className="text-danger ml-2">■</span> não cumprido
        <span className="text-accent ml-2">■</span> em andamento
      </p>
      <div className="h-72">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data}>
            <CartesianGrid stroke={grid} vertical={false} />
            <XAxis dataKey="period" stroke="#777" fontSize={11} minTickGap={20} />
            <YAxis stroke="#777" fontSize={11} width={80} tickFormatter={v => formatGoalValue(v, 'BRL').replace(',00', '')} />
            <Tooltip formatter={(v: number) => [formatGoalValue(v, 'BRL'), 'Aporte líquido']}
              cursor={{ fill: isDark ? '#ffffff10' : '#00000008' }}
              contentStyle={{ background: isDark ? '#111' : '#fff', border: `1px solid ${grid}` }} />
            <ReferenceLine y={target} stroke="#38a169" strokeDasharray="6 4"
              label={{ value: `Alvo ${formatGoalValue(target, 'BRL')}`, position: 'insideTopRight', fill: '#38a169', fontSize: 11 }} />
            {tolerance > 0 && <ReferenceLine y={target - tolerance} stroke="#38a169" strokeOpacity={0.4} strokeDasharray="2 4" />}
            <Bar dataKey="amount" radius={[3, 3, 0, 0]}>
              {data.map(d => (
                <Cell key={d.period} fill={!d.closed ? '#c9a84c' : d.fulfilled ? '#38a169' : '#e53e3e'}
                  fillOpacity={d.closed ? 0.85 : 0.6} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </>
  );
};

const ItemsTable = ({ items }: { items: Record<string, unknown>[] }) => {
  if (items.length === 0) return null;
  const keys = Object.keys(items[0]);
  const LABEL: Record<string, string> = {
    period: 'Período', amount: 'Aporte', closed: 'Fechado', fulfilled: 'Cumprido', item: 'Item', value: 'Valor',
    share: 'Participação', above: 'Acima', excessPp: 'Excesso', reduce: 'Reduzir (informativo)', current: '% atual',
    ideal: '% ideal', deviation: 'Desvio', outside: 'Fora', issue: 'Pendência', date: 'Data', weight: 'Peso', count: 'Qtde.',
  };
  // cores (revisão de 26/10/2026): desvio pelo sinal, como o Rebalanceamento; excesso e "fora" em vermelho
  const tone = (k: string, v: unknown): string => {
    if (v === null || v === undefined) return 'text-gray-300';
    if (k === 'deviation') return getReturnColor(Number(v));
    if (k === 'excessPp' || k === 'reduce') return Number(v) > 0 ? 'text-danger' : 'text-gray-300';
    if (k === 'outside' || k === 'above') return v ? 'text-danger' : 'text-success';
    if (k === 'fulfilled') return v ? 'text-success' : 'text-danger';
    return 'text-gray-300';
  };
  const fmt = (k: string, v: unknown) => {
    if (v === null || v === undefined) return '—';
    if (typeof v === 'boolean') return v ? 'Sim' : 'Não';
    if (k === 'issue') return ISSUE_LABEL[v as string] ?? String(v);
    if (k === 'date') return formatDateBR(v as string);
    if (['amount', 'value', 'reduce'].includes(k)) return formatGoalValue(Number(v), 'BRL');
    if (['share', 'current', 'ideal', 'weight'].includes(k)) return formatGoalValue(Number(v), 'PERCENT');
    if (k === 'deviation') return `${Number(v) > 0 ? '+' : ''}${formatGoalValue(Number(v), 'PP')}`;
    if (k === 'excessPp') return formatGoalValue(Number(v), 'PP');
    return String(v);
  };
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs text-gray-400 border-b border-surface-light">
            {keys.map(k => <th key={k} className="py-2 pr-4 font-medium">{LABEL[k] ?? k}</th>)}
          </tr>
        </thead>
        <tbody>
          {items.map((row, i) => (
            <tr key={i} className="border-b border-surface-light/50">
              {keys.map(k => <td key={k} className={`py-1.5 pr-4 ${tone(k, row[k])}`}>{fmt(k, row[k])}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

/** {@code limit}: meta de limite (não passar dele) — o alvo vira o nome do limite e a barra mostra o uso dele. */
const Situation = ({ e, limit }: { e: GoalEvaluation; limit: string | null }) => {
  const used = limit ? limitUsage(e.indicator, e.target) : null;
  return (
  <Card title="Situação" titleInfo="Calculada agora com os dados da carteira. Observado = dado registrado; Calculado = regra fixa; Estimativa = depende de premissa.">
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p className="text-xs text-gray-400">{e.indicatorLabel}</p>
        <p className="text-3xl font-semibold text-white">{formatGoalValue(e.indicator, e.unit)}</p>
        {e.target !== null && <p className="text-sm text-gray-400">{limit ?? 'Alvo'}: {formatGoalValue(e.target, e.unit)}</p>}
      </div>
      <div className="flex flex-col items-end gap-1">
        <Badge variant={SITUATION[e.situation].variant}>{SITUATION[e.situation].label}</Badge>
        <Badge variant={e.quality === 'COMPLETA' ? 'success' : e.quality === 'PARCIAL' ? 'warning' : 'default'}>{QUALITY[e.quality]}</Badge>
      </div>
    </div>
    {used !== null ? (
      <div className="mt-4">
        <div className="h-3 w-full rounded-full bg-surface-light overflow-hidden">
          <div className={limitBarClass(used)} style={{ width: `${barWidth(used)}%` }} />
        </div>
        <p className="text-xs text-gray-400 mt-1">{formatGoalValue(used, 'PERCENT')} do limite</p>
      </div>
    ) : e.progressPct !== null && (
      <div className="mt-4">
        <div className="h-3 w-full rounded-full bg-surface-light overflow-hidden">
          <div className={e.achieved ? 'h-full bg-success' : 'h-full bg-accent'} style={{ width: `${barWidth(e.progressPct)}%` }} />
        </div>
        <p className="text-xs text-gray-400 mt-1">{formatGoalValue(e.progressPct, 'PERCENT')} do alvo</p>
      </div>
    )}
    {e.remaining !== null && e.remainingLabel && (
      <p className="text-sm text-gray-300 mt-3">{e.remainingLabel}: <span className="text-white font-medium">
        {formatGoalValue(e.remaining, e.remainingUnit ?? e.unit)}</span></p>
    )}
    {e.situationNote && <p className="text-sm text-gray-400 mt-2">{e.situationNote}</p>}
    {e.issues.length > 0 && (
      <ul className="mt-3 text-xs text-yellow-500 list-disc pl-4 space-y-0.5">{e.issues.map(i => <li key={i}>{i}</li>)}</ul>
    )}
  </Card>
  );
};

/**
 * Detalhe da meta (docs/planos/PLANO_METAS.md, Anexo A 12): situação, falta, decomposição, projeção com as premissas,
 * evolução (recalculada, com o alvo e as fotografias), alterações e a origem e a data de cada dado. A memória de
 * cálculo existe na API e não aparece na tela (D21).
 */
export const GoalDetailPage = () => {
  // nomes curtos dos fundos (o ticker do fundo é o CNPJ — PLANO_FUNDOS_INVESTIMENTO.md, D3)
  useAssetLabel();
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const { isDark } = useTheme();
  const [editing, setEditing] = useState(false);

  const { data: goal, isLoading, isError } = useQuery({ queryKey: ['goals', 'detail', id], queryFn: () => goalsApi.get(id) });
  const { data: catalog } = useQuery({ queryKey: ['goals', 'catalog'], queryFn: goalsApi.catalog, staleTime: Infinity });
  const { data: history } = useQuery({ queryKey: ['goals', 'history', id], queryFn: () => goalsApi.history(id), enabled: !!goal });
  const { data: events } = useQuery({ queryKey: ['goals', 'events', id], queryFn: () => goalsApi.events(id), enabled: !!goal });

  const act = useMutation({
    mutationFn: (a: GoalAction) => goalsApi.transition(id, a),
    onSuccess: g => { queryClient.invalidateQueries({ queryKey: ['goals'] }); showToast(`Meta ${LIFECYCLE[g.lifecycle].toLowerCase()}`, 'success'); },
    onError: e => showToast(apiMessage(e) ?? 'Não foi possível', 'error'),
  });
  const remove = useMutation({
    mutationFn: () => goalsApi.remove(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['goals'] }); showToast('Meta excluída', 'success'); navigate('/goals'); },
    onError: e => showToast(apiMessage(e) ?? 'Não foi possível excluir', 'error'),
  });

  /**
   * Eixo de tempo contínuo (não por categoria): evolução, linhas de apoio, fotografias e projeção ficam cada uma na
   * sua data — com o eixo por categoria, séries de densidades diferentes se deslocavam umas das outras.
   */
  const chart = useMemo(() => {
    if (!history) return [];
    const rows = new Map<number, Record<string, number | null>>();
    const ts = (iso: string) => new Date(`${iso.slice(0, 10)}T12:00:00`).getTime();
    const put = (iso: string, key: string, v: number | null) => {
      const t = ts(iso);
      const row = rows.get(t) ?? { t };
      row[key] = v;
      rows.set(t, row);
    };
    history.series.forEach(p => put(p.date, 'value', Number(p.value)));
    (history.extras ?? []).forEach(x => x.points.forEach(p => put(p.date, `x_${x.key}`, Number(p.value))));
    // fotografias só quando medem o mesmo que a evolução (aposentadoria com renda, superar índice: grandezas diferentes)
    if (history.comparable) history.snapshots.forEach(s => s.indicator !== null && put(s.date, 'snapshot', Number(s.indicator)));
    // Projeção (estimativa) tracejada a partir de hoje, quando a meta tem e está na mesma unidade da evolução
    const path = goal?.evaluation?.projection?.path ?? [];
    if (path.length > 1 && history.unit === 'BRL') {
      const today = new Date();
      // sem prazo, a projeção vai até o alvo — às vezes décadas, e espremia o histórico: mostra até 5 anos
      const horizon = goal?.targetDate ? Infinity : 60;
      for (const p of path.filter(x => x.month <= horizon)) {
        const d = new Date(today.getFullYear(), today.getMonth() + p.month, today.getDate(), 12);
        put(d.toISOString(), 'projection', Number(p.value));
      }
    }
    const sorted = [...rows.values()].sort((a, b) => Number(a.t) - Number(b.t));
    // a evolução fecha no último mês (ou dia) com dado; a fotografia de hoje fica à direita: um trecho pontilhado
    // liga o último ponto da evolução à primeira fotografia depois dele
    const lastValue = [...sorted].reverse().find(r => r.value !== null && r.value !== undefined);
    const nextSnap = lastValue && sorted.find(r => Number(r.t) > Number(lastValue.t) && r.snapshot !== null && r.snapshot !== undefined);
    if (lastValue && nextSnap) {
      lastValue.link = lastValue.value as number;
      nextSnap.link = nextSnap.snapshot as number;
    }
    // alvo fixo como linha (aparece na legenda e sempre dentro do gráfico)
    const targets = history.targets ?? [];
    const target = targets.length ? targets[targets.length - 1].target : null;
    if (target !== null && history.fixedTarget && history.unit === goal?.evaluation?.unit) {
      sorted.forEach(r => { r.target = Number(target); });
    }
    return sorted;
  }, [history, goal]);

  if (isLoading) return <div className="flex justify-center py-12"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  if (isError || !goal) return <Card><p className="text-gray-400">Meta não encontrada. <Link to="/goals" className="text-accent">Voltar</Link></p></Card>;

  const e = goal.evaluation;
  const final = goal.finalResult as Record<string, unknown> | null;
  const lc = goal.lifecycle;
  const unit = history?.unit ?? e?.unit ?? 'BRL';
  const targets = history?.targets ?? [];
  const lastTarget = targets.length ? targets[targets.length - 1].target : null;
  const has = (key: string) => chart.some(r => r[key] !== null && r[key] !== undefined);
  // limite (não passar dele): linha vermelha com o nome do limite, no lugar do "Alvo" verde
  const LIMITS: Record<string, string> = {
    RIS_2: 'Queda máxima', RIS_1: 'Participação máxima', RIS_3: 'Máximo de pendências', ALO_2: 'Desvio máximo',
  };
  const limit = LIMITS[goal.type] ? { label: LIMITS[goal.type] } : null;
  // queda desde o pico: o eixo cresce para baixo (o pico é o zero, lá em cima)
  const drawdown = goal.type === 'RIS_2';
  // aporte periódico: barras por período no lugar da lista e do acumulado
  const periodic = goal.type === 'APO_2';
  const grid = isDark ? '#2a2a2a' : '#e8e8e0';

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to="/goals" className="text-sm text-gray-400 hover:text-white inline-flex items-center gap-1"><ArrowLeft className="w-4 h-4" />Metas</Link>
          <h2 className="text-xl font-semibold text-white mt-1">{assetText(goal.name)}</h2>
          <p className="text-sm text-gray-400">{goal.categoryLabel} · {goal.typeLabel} · {LIFECYCLE[lc]}{goal.revision > 1 ? ` · ${goal.revision}ª versão` : ''}</p>
          <p className="text-sm text-gray-300 mt-1">{e?.objective ?? (final?.objective as string)}</p>
          {goal.scopeLabel && <p className="text-xs text-gray-500">{assetText(goal.scopeLabel)}</p>}
        </div>
        <div className="flex flex-wrap gap-2">
          {(lc === 'ATIVA' || lc === 'PAUSADA') && <Button variant="outline" size="sm" onClick={() => setEditing(true)} disabled={!catalog}><Pencil className="w-4 h-4 mr-1" />Editar</Button>}
          {lc === 'CONCLUIDA' && <Button variant="outline" size="sm" disabled={!catalog}
            onClick={() => window.confirm('Editar reabre a meta concluída (para corrigir um cadastro errado). Se o alvo continuar atingido, ela conclui de novo. Continuar?') && setEditing(true)}>
            <Pencil className="w-4 h-4 mr-1" />Editar</Button>}
          {lc === 'ATIVA' && <Button variant="outline" size="sm" onClick={() => act.mutate('pause')}><Pause className="w-4 h-4 mr-1" />Pausar</Button>}
          {lc === 'PAUSADA' && <Button variant="outline" size="sm" onClick={() => act.mutate('resume')}><Play className="w-4 h-4 mr-1" />Retomar</Button>}
          {(lc === 'ATIVA' || lc === 'PAUSADA') && <Button variant="outline" size="sm" onClick={() => window.confirm('Concluir a meta? O resultado de hoje fica congelado.') && act.mutate('complete')}><CheckCircle2 className="w-4 h-4 mr-1" />Concluir</Button>}
          {lc !== 'ARQUIVADA' && <Button variant="outline" size="sm" onClick={() => act.mutate('archive')}><Archive className="w-4 h-4 mr-1" />Arquivar</Button>}
          {lc === 'ARQUIVADA' && <Button variant="outline" size="sm" onClick={() => act.mutate('unarchive')}><ArchiveRestore className="w-4 h-4 mr-1" />Desarquivar</Button>}
          {lc === 'ARQUIVADA' && <Button variant="danger" size="sm" onClick={() => window.confirm(`Excluir de vez a meta "${goal.name}"? O histórico some junto.`) && remove.mutate()}><Trash2 className="w-4 h-4 mr-1" />Excluir</Button>}
        </div>
      </div>

      {lc === 'PAUSADA' && <p className="text-sm text-yellow-500">Meta pausada: a situação abaixo é calculada agora, mas a fotografia diária e os avisos estão parados.</p>}

      {e ? <Situation e={e} limit={limit?.label ?? null} /> : final && (
        <Card title="Resultado final" titleInfo="Congelado na conclusão: se a carteira mudar depois, a meta continua concluída.">
          <p className="text-3xl font-semibold text-white">{formatGoalValue(final.indicator as number, final.unit as GoalUnit)}</p>
          <p className="text-sm text-gray-400">{final.indicatorLabel as string} em {formatDateBR(final.date as string)}
            {final.by === 'SISTEMA' ? ' — concluída automaticamente ao atingir o alvo' : ''}</p>
        </Card>
      )}

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        {e && e.figures.length > 0 && (
          <Card title="Números da meta">
            {e.figures.map(f => <FigureRow key={f.key} f={f} />)}
          </Card>
        )}
        {e?.projection && (
          <Card title="Projeção" titleInfo="Estimativa — não é garantia. Premissas automáticas são recalculadas a cada dia; informadas ficam fixas na meta (edite para mudar).">
            {e.projection.suspended && <p className="text-sm text-gray-400 mb-3">{e.projection.suspended}</p>}
            {e.projection.figures.map(f => <FigureRow key={f.key} f={f} />)}
            {e.projection.assumptions.length > 0 && (
              <div className="mt-4">
                <p className="text-xs uppercase tracking-wide text-gray-500 mb-1">Premissas</p>
                {e.projection.assumptions.map(a => (
                  <div key={a.key} className="flex justify-between gap-3 py-1 text-sm">
                    <span className="text-gray-300">{a.label} <span className="text-xs text-gray-500">({a.origin === 'INFORMADA' ? 'informada' : 'automática'}{a.reference ? ` · ${a.reference}` : ''}{a.referenceDate ? ` · ${formatDateBR(a.referenceDate)}` : ''})</span></span>
                    <span className={a.origin === 'INFORMADA' ? 'text-accent' : 'text-white'}>{formatGoalValue(a.value, a.unit)}</span>
                  </div>
                ))}
              </div>
            )}
          </Card>
        )}
      </div>

      {e && e.items.length > 0 && (periodic ? (
        <Card title="Aporte por período"
          titleInfo="Aporte líquido (compras − vendas) de cada período do escopo, contra o valor da meta. A linha verde tracejada é o alvo; a mais fraca, o alvo menos a tolerância.">
          <PeriodBars items={e.items} target={Number(goal.targetValue ?? 0)} tolerance={Number(goal.params?.tolerance ?? 0)} isDark={isDark} />
        </Card>
      ) : (
        <Card title="Detalhamento"><ItemsTable items={e.items} /></Card>
      ))}

      {!periodic && <Card title={history?.seriesLabel ?? 'Evolução'} titleInfo="Linha cheia: evolução recalculada com os dados de hoje (inclusive antes da meta existir). Pontos: a fotografia de cada dia, como a meta estava. Linha dourada tracejada: a projeção com as premissas (estimativa). Linha verde tracejada: o alvo.">
        {history?.note && <p className="text-xs text-gray-500 mb-2">{history.note}</p>}
        {chart.length > 1 ? (
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={chart}>
                <CartesianGrid stroke={grid} vertical={false} />
                <XAxis dataKey="t" type="number" scale="time" domain={['dataMin', 'dataMax']}
                  tickFormatter={t => formatDateBR(new Date(t).toISOString()).slice(3)} stroke="#777" fontSize={11} minTickGap={40} />
                <YAxis stroke="#777" fontSize={11} width={80} reversed={drawdown}
                  tickFormatter={v => `${drawdown && Number(v) > 0 ? '−' : ''}${formatGoalValue(v, unit as GoalUnit, 0)}`} />
                <Tooltip formatter={(v: number) => formatGoalValue(v, unit as GoalUnit)} labelFormatter={t => formatDateBR(new Date(t as number).toISOString())}
                  contentStyle={{ background: isDark ? '#111' : '#fff', border: `1px solid ${grid}` }} />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                {(history?.extras ?? []).map((x, i) => (
                  <Line key={x.key} type="monotone" dataKey={`x_${x.key}`} name={x.label} dot={false} connectNulls
                    stroke={i === 0 ? '#4299e1' : '#9f7aea'} strokeWidth={x.key === 'monthly' ? 1 : 2}
                    strokeOpacity={x.key === 'monthly' ? 0.5 : 1} />
                ))}
                {has('value') && <Line type="monotone" dataKey="value" name={history?.seriesLabel ?? 'Evolução'} stroke="#c9a84c" dot={false} strokeWidth={2} connectNulls />}
                {has('projection') && <Line type="monotone" dataKey="projection" name="Projeção" stroke="#c9a84c" strokeOpacity={0.6} strokeDasharray="6 4" dot={false} strokeWidth={2} connectNulls />}
                {has('snapshot') && <Line type="linear" dataKey="snapshot" name="Fotografias" stroke="#9a9b9c" strokeDasharray="2 3" strokeWidth={1}
                  dot={{ r: 3, fill: '#9a9b9c', strokeWidth: 0 }} connectNulls />}
                {has('link') && <Line type="linear" dataKey="link" legendType="none" tooltipType="none" stroke="#9a9b9c" strokeDasharray="2 3" strokeWidth={1} dot={false} connectNulls />}
                {has('target') && <Line type="linear" dataKey="target" name={`${limit ? limit.label : 'Alvo'} ${formatGoalValue(Number(lastTarget), unit as GoalUnit)}`}
                  stroke={limit ? '#e53e3e' : '#38a169'} strokeDasharray="6 4" strokeWidth={1.5} dot={false} />}
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        ) : <p className="text-sm text-gray-500">Sem pontos suficientes para o gráfico.</p>}
      </Card>}

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        <Card title="Alterações e resultados">
          {(events ?? []).slice().reverse().map(ev => (
            <div key={ev.id} className="flex justify-between gap-3 py-1.5 border-b border-surface-light last:border-0 text-sm">
              <span className="text-gray-300">{EVENT_LABEL[ev.kind] ?? ev.kind} <span className="text-xs text-gray-500">{ev.revision}ª versão</span></span>
              <span className="text-gray-500">{formatDateBR(ev.occurredAt)}</span>
            </div>
          ))}
        </Card>
        {e && (
          <Card title="Origem dos dados" titleInfo="De onde vêm os números e até quando os dados valem.">
            {e.sources.map(s => (
              <div key={s.label} className="flex justify-between py-1.5 text-sm border-b border-surface-light last:border-0">
                <span className="text-gray-300">{s.label}</span><span className="text-gray-400">{formatDateBR(s.date)}</span>
              </div>
            ))}
            <p className="text-xs text-gray-500 mt-3 flex items-center gap-1">
              Os mesmos números da Carteira, de Dividendos e da Rentabilidade.
              <InfoTooltip content="Patrimônio = valor atual da Carteira; renda = proventos líquidos da tela Dividendos (sem amortização); rentabilidade = a da tela Rentabilidade; estratégia = Rebalanceamento." />
            </p>
          </Card>
        )}
      </div>

      {editing && catalog && <GoalFormModal isOpen goal={goal} catalog={catalog} onClose={() => setEditing(false)} />}
    </div>
  );
};
