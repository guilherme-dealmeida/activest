import { clsx } from 'clsx';
import { signedPct } from './ceilingFormat';

/** Margem do teto e do ideal contra um preço: positiva = o preço está abaixo (há margem). */
export const MarginPair = ({ ceiling, ideal }: { ceiling: number | null; ideal: number | null }) => {
  if (ceiling == null && ideal == null) return null;
  const color = (v: number | null) => (v == null ? 'text-gray-500' : v >= 0 ? 'text-success' : 'text-danger');
  return (
    <div className="text-xs tabular-nums whitespace-nowrap">
      <span className={clsx(color(ceiling))} title="Margem do Preço Teto: teto ÷ preço − 1">T: {signedPct(ceiling, 1)}</span>
      <span className="text-gray-600"> · </span>
      <span className={clsx(color(ideal))} title="Margem do Preço Ideal: ideal ÷ preço − 1">I: {signedPct(ideal, 1)}</span>
    </div>
  );
};

