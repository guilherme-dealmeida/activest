import { Link } from 'react-router-dom';
import { Badge } from '@/components/ui/Badge';
import { Goal } from '@/types/goals';
import { formatDateBR, formatGoalValue, LIFECYCLE, SITUATION } from './goalFormat';

/**
 * Cartão da meta na lista (docs/planos/PLANO_METAS.md, Anexo A 12): objetivo em uma frase, indicador atual × alvo,
 * barra de progresso (crescente) ou a posição em relação ao limite (controle, G5), situação e qualidade dos dados.
 */
export const GoalCard = ({ goal }: { goal: Goal }) => {
  const e = goal.evaluation;
  // aporte periódico guarda o fim do período em params.endDate (não em targetDate)
  const deadline = goal.targetDate ?? (typeof goal.params?.endDate === 'string' ? goal.params.endDate : null);
  const final = goal.finalResult as Record<string, unknown> | null;
  const dates = e?.sources?.map(s => s.date).sort() ?? [];
  const updated = dates.length ? dates[dates.length - 1] : undefined;
  return (
    <Link to={`/goals/${goal.id}`}
      className="block bg-surface rounded-xl border border-surface-light p-5 hover:border-accent/50 transition-colors">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs text-gray-400">{goal.categoryLabel} · {goal.typeLabel}</p>
          <h3 className="text-white font-semibold truncate">{goal.name}</h3>
        </div>
        <div className="flex flex-col items-end gap-1 shrink-0">
          {e && goal.lifecycle === 'ATIVA' && <Badge variant={SITUATION[e.situation].variant}>{SITUATION[e.situation].label}</Badge>}
          {goal.lifecycle !== 'ATIVA' && <Badge>{LIFECYCLE[goal.lifecycle]}</Badge>}
          {e?.quality === 'PARCIAL' && <Badge variant="warning">Dados parciais</Badge>}
        </div>
      </div>
      <p className="text-sm text-gray-300 mt-2">{e?.objective ?? (final?.objective as string) ?? ''}</p>
      {e && (
        <div className="mt-3">
          <div className="flex items-baseline justify-between gap-2">
            <span className="text-lg font-semibold text-white">{formatGoalValue(e.indicator, e.unit)}</span>
            {e.target !== null && <span className="text-xs text-gray-400">alvo {formatGoalValue(e.target, e.unit)}</span>}
          </div>
          <p className="text-xs text-gray-500">{e.indicatorLabel}</p>
          {e.progressPct !== null && (
            <div className="mt-2 h-2 w-full rounded-full bg-surface-light overflow-hidden" title={`${formatGoalValue(e.progressPct, 'PERCENT')}`}>
              <div className={e.achieved ? 'h-full bg-success' : 'h-full bg-accent'} style={{ width: `${Math.min(100, Number(e.progressPct))}%` }} />
            </div>
          )}
          {e.remaining !== null && e.remainingLabel && (
            <p className="text-xs text-gray-400 mt-1">
              {e.remainingLabel}: {formatGoalValue(e.remaining, e.remainingUnit ?? e.unit)}
            </p>
          )}
        </div>
      )}
      {!e && final && (
        <p className="text-sm text-gray-400 mt-3">
          Resultado final em {formatDateBR(final.date as string)}: {formatGoalValue(final.indicator as number, final.unit as never)}
        </p>
      )}
      <div className="flex justify-between text-xs text-gray-500 mt-3">
        <span>{deadline ? `Prazo ${formatDateBR(deadline)}` : 'Sem prazo'}</span>
        {updated && <span>Dados até {formatDateBR(updated)}</span>}
      </div>
    </Link>
  );
};
