import { TaskExecutionStatus } from '@/types';
import { STATUS_CLASS, STATUS_HINT } from './taskFormat';

/** Status da rodada: o valor em inglês (D2), com a cor da situação e a explicação no hover. */
export const StatusBadge = ({ status }: { status: TaskExecutionStatus }) => (
  <span
    className={`inline-flex items-center rounded border px-2 py-0.5 text-xs font-medium cursor-help ${STATUS_CLASS[status]}`}
    title={STATUS_HINT[status]}
  >
    {status}
  </span>
);
