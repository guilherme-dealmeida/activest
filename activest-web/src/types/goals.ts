// Metas financeiras (docs/planos/PLANO_METAS.md, Anexo A 11 e 12): o usuário define o objetivo, o Activest calcula.

export type GoalLifecycle = 'ATIVA' | 'PAUSADA' | 'CONCLUIDA' | 'ARQUIVADA';
export type GoalNature = 'CRESCENTE' | 'CONTROLE' | 'SEQUENCIA' | 'ESTIMATIVA';
export type GoalSituation =
  | 'NO_RITMO' | 'EM_ANDAMENTO' | 'ATRASADA' | 'PRAZO_VENCIDO' | 'DENTRO_DO_OBJETIVO' | 'FORA_DO_OBJETIVO'
  | 'ATINGIDA' | 'ESTIMATIVA' | 'SEM_DADOS';
export type GoalUnit = 'BRL' | 'PERCENT' | 'PP' | 'SHARES' | 'COUNT' | 'RATIO';
export type GoalQuality = 'COMPLETA' | 'PARCIAL' | 'INDISPONIVEL';
export type GoalNumberNature = 'OBSERVADO' | 'CALCULADO' | 'ESTIMATIVA';
export type GoalFieldKind =
  | 'MONEY' | 'PERCENT' | 'NUMBER' | 'INTEGER' | 'DATE' | 'BOOLEAN' | 'CHOICE' | 'MULTI_CHOICE' | 'TICKER' | 'TEXT';

export interface Option {
  value: string;
  label: string;
}

export interface GoalField {
  key: string;
  label: string;
  kind: GoalFieldKind;
  required: boolean;
  defaultValue: unknown;
  options: Option[] | null;
  help: string | null;
  min: number | null;
  max: number | null;
  /** "campo=VALOR1,VALOR2": obrigatório só quando o outro campo tem um destes valores. */
  requiredIf: string | null;
}

export interface GoalTypeInfo {
  type: string;
  code: string;
  label: string;
  category: string;
  categoryLabel: string;
  nature: GoalNature;
  projection: boolean;
  description: string;
  fields: GoalField[];
  scopes: Option[];
  reservation: 'EXPLICIT' | 'DEFAULT_WITHOUT' | 'DEFAULT_WITH' | 'NONE';
  calculates: string[];
  data: string[];
  limitations: string[];
}

export interface GoalCatalog {
  categories: Option[];
  types: GoalTypeInfo[];
}

export interface GoalFigure {
  key: string;
  label: string;
  value: number | null;
  unit: GoalUnit;
  nature: GoalNumberNature;
  note: string | null;
}

export interface GoalAssumption {
  key: string;
  label: string;
  value: number | null;
  unit: GoalUnit;
  origin: 'AUTOMATICA' | 'INFORMADA';
  reference: string | null;
  referenceDate: string | null;
}

export interface GoalProjection {
  assumptions: GoalAssumption[];
  figures: GoalFigure[];
  suspended: string | null;
  path: { month: number; value: number }[];
}

export interface GoalEvaluation {
  indicator: number | null;
  indicatorLabel: string;
  unit: GoalUnit;
  target: number | null;
  progressPct: number | null;
  remaining: number | null;
  remainingLabel: string | null;
  /** Unidade do que falta (p.p. nas diferenças de percentuais; R$ na renda que falta numa meta em %). */
  remainingUnit: GoalUnit | null;
  achieved: boolean;
  situation: GoalSituation;
  situationNote: string | null;
  quality: GoalQuality;
  issues: string[];
  objective: string;
  figures: GoalFigure[];
  items: Record<string, unknown>[];
  projection: GoalProjection | null;
  sources: { label: string; date: string }[];
  steps: string[];
}

export interface GoalScopeValue {
  kind: string;
  values: string[];
  reservation: 'WITH' | 'WITHOUT' | 'ONLY' | null;
  excludeTypes: string[];
  excludeCategories: string[];
}

export interface Goal {
  id: string;
  name: string;
  type: string;
  code: string;
  typeLabel: string;
  category: string;
  categoryLabel: string;
  nature: GoalNature;
  lifecycle: GoalLifecycle;
  targetValue: number | null;
  targetDate: string | null;
  scope: GoalScopeValue;
  scopeLabel: string | null;
  params: Record<string, unknown>;
  notifications: boolean;
  notes: string | null;
  revision: number;
  achievedOn: string | null;
  finalResult: Record<string, unknown> | null;
  createdAt: string;
  updatedAt: string;
  evaluation: GoalEvaluation | null;
}

export interface GoalSummary {
  active: number;
  paused: number;
  completed: number;
  archived: number;
  onTrack: number;
  behind: number;
  outside: number;
  noData: number;
  achieved: number;
  /** Em andamento ou estimativa (azul). */
  inProgress: number;
}

export interface GoalList {
  summary: GoalSummary;
  goals: Goal[];
}

export interface GoalRequest {
  name?: string;
  type: string;
  targetValue?: number | null;
  targetDate?: string | null;
  scope?: Partial<GoalScopeValue>;
  params?: Record<string, unknown>;
  notifications?: boolean;
  notes?: string | null;
}

export interface GoalPreview {
  objective: string;
  scope: GoalScopeValue;
  scopeLabel: string | null;
  evaluation: GoalEvaluation;
}

export interface GoalEvent {
  id: number;
  kind: string;
  revision: number;
  definition: Record<string, unknown> | null;
  detail: Record<string, unknown> | null;
  occurredAt: string;
}

export interface GoalHistory {
  unit: GoalUnit | null;
  seriesLabel: string | null;
  series: { date: string; value: number }[];
  targets: { revision: number; target: number | null; from: string }[];
  snapshots: {
    date: string; revision: number; indicator: number | null; target: number | null; progressPct: number | null;
    situation: GoalSituation; quality: GoalQuality;
  }[];
  note: string | null;
  /** A evolução mede o mesmo que o indicador e o alvo (falso na aposentadoria com renda desejada). */
  comparable: boolean;
  /** Linhas de apoio na mesma unidade (o índice em superar um índice; os proventos do mês nas metas de renda). */
  extras: { key: string; label: string; points: { date: string; value: number }[] }[];
  /** O alvo é um número fixo (vira linha); falso quando ele anda (o aporte médio em "Renda maior que os aportes"). */
  fixedTarget: boolean;
}

export type GoalScopeOptions = { options: Record<string, Option[]> };
