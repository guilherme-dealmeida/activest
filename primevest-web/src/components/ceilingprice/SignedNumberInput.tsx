import { MaskedNumberInput } from '@/components/ui/MaskedNumberInput';
import { clsx } from 'clsx';

interface Props {
  value: number | null;
  onChange: (v: number) => void;
  decimals?: number;
  adornment?: { text: string; side: 'left' | 'right' };
  /** Aceita negativo (lucro com prejuízo, ROE negativo): botão ± ao lado. */
  allowNegative?: boolean;
  className?: string;
  ariaLabel?: string;
  error?: boolean;
}

/**
 * Campo numérico do Preço Teto: a máscara do projeto ({@link MaskedNumberInput}, que só lê dígitos) com um botão de
 * sinal — prejuízo trimestral e premissa negativa entram como são (validação 6 do pedido: nada é limitado).
 */
export const SignedNumberInput = ({ value, onChange, decimals = 2, adornment, allowNegative = true, className,
                                    ariaLabel, error }: Props) => {
  const negative = value != null && value < 0;
  const abs = value == null ? null : Math.abs(value);
  return (
    <div className={clsx('flex items-stretch gap-1', className)}>
      {allowNegative && (
        <button
          type="button"
          title={negative ? 'Negativo — clique para positivo' : 'Positivo — clique para negativo'}
          aria-label="Trocar sinal"
          onClick={() => onChange(-(value ?? 0))}
          className={clsx('w-8 shrink-0 rounded-md border text-sm font-semibold',
            negative ? 'border-danger/50 text-danger bg-danger/10' : 'border-success/50 text-success bg-success/10')}
        >
          {negative ? '−' : '+'}
        </button>
      )}
      <MaskedNumberInput
        aria-label={ariaLabel}
        value={abs}
        decimals={decimals}
        adornment={adornment}
        onChange={v => onChange(negative ? -v : v)}
        className={clsx(error && 'border-danger')}
      />
    </div>
  );
};
