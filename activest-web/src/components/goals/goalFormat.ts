import { formatCurrency } from '@/utils/format';
import { GoalLifecycle, GoalNumberNature, GoalQuality, GoalSituation, GoalUnit } from '@/types/goals';

/** Número da meta na unidade dela (D19: R$ 2 casas, % 1 casa — rentabilidade 2). */
export const formatGoalValue = (value: number | null | undefined, unit: GoalUnit | null | undefined, digits?: number): string => {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  const n = Number(value);
  switch (unit) {
    case 'BRL':
      return formatCurrency(n);
    case 'PERCENT':
      return `${n.toLocaleString('pt-BR', { minimumFractionDigits: digits ?? 1, maximumFractionDigits: digits ?? 2 })}%`;
    case 'PP':
      return `${n.toLocaleString('pt-BR', { minimumFractionDigits: digits ?? 1, maximumFractionDigits: digits ?? 2 })} p.p.`;
    case 'SHARES':
      return n.toLocaleString('pt-BR', { maximumFractionDigits: 8 });
    case 'COUNT':
      return n.toLocaleString('pt-BR', { maximumFractionDigits: 0 });
    case 'RATIO':
      return n.toLocaleString('pt-BR', { maximumFractionDigits: 4 });
    default:
      return n.toLocaleString('pt-BR');
  }
};

export const SITUATION: Record<GoalSituation, { label: string; variant: 'success' | 'danger' | 'warning' | 'info' | 'default' }> = {
  NO_RITMO: { label: 'No ritmo', variant: 'success' },
  EM_ANDAMENTO: { label: 'Em andamento', variant: 'info' },
  ATRASADA: { label: 'Atrasada', variant: 'warning' },
  PRAZO_VENCIDO: { label: 'Prazo vencido', variant: 'danger' },
  DENTRO_DO_OBJETIVO: { label: 'Dentro do objetivo', variant: 'success' },
  FORA_DO_OBJETIVO: { label: 'Fora do objetivo', variant: 'danger' },
  ATINGIDA: { label: 'Atingida', variant: 'success' },
  ESTIMATIVA: { label: 'Estimativa', variant: 'info' },
  SEM_DADOS: { label: 'Sem dados suficientes', variant: 'default' },
};

export const LIFECYCLE: Record<GoalLifecycle, string> = {
  ATIVA: 'Ativa',
  PAUSADA: 'Pausada',
  CONCLUIDA: 'Concluída',
  ARQUIVADA: 'Arquivada',
};

export const QUALITY: Record<GoalQuality, string> = {
  COMPLETA: 'Dados completos',
  PARCIAL: 'Dados parciais',
  INDISPONIVEL: 'Sem dados suficientes',
};

/** G2: Observado · Calculado · Estimativa — como Padrão/Informado/Calculado do Preço Teto. */
export const NATURE: Record<GoalNumberNature, { label: string; className: string; help: string }> = {
  OBSERVADO: { label: 'Observado', className: 'text-gray-300', help: 'Dado registrado: transação, provento pago, cotação, índice publicado.' },
  CALCULADO: { label: 'Calculado', className: 'text-white', help: 'Derivado dos dados observados por regra fixa.' },
  ESTIMATIVA: { label: 'Estimativa', className: 'text-accent', help: 'Depende de premissa: não é garantia.' },
};

export const formatDateBR = (iso: string | null | undefined): string => {
  if (!iso) return '—';
  const [y, m, d] = iso.slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
};
