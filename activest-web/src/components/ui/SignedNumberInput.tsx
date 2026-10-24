import { useState } from 'react';
import { MaskedNumberInput } from '@/components/ui/MaskedNumberInput';
import { clsx } from 'clsx';

interface Props {
  value: number | null;
  onChange: (v: number) => void;
  decimals?: number;
  adornment?: { text: string; side: 'left' | 'right' };
  /** Aceita negativo (lucro com prejuízo, ROE negativo): botão ± ao lado. */
  allowNegative?: boolean;
  /** Campo que pode ficar vazio (filtros): ver {@link MaskedNumberInput}. */
  allowEmpty?: boolean;
  onClear?: () => void;
  placeholder?: string;
  /** "sm": campo e botão mais baixos e fonte menor (painel de filtros). */
  size?: 'md' | 'sm';
  className?: string;
  ariaLabel?: string;
  error?: boolean;
}

/**
 * Campo numérico com sinal: a máscara do projeto ({@link MaskedNumberInput}, que só lê dígitos) com um botão ± —
 * prejuízo trimestral e premissa negativa entram como são (Preço Teto); faixa de indicador que pode ser negativo
 * (filtros da listagem de ativos). Com o campo vazio ou em zero, o sinal escolhido fica guardado e vale para o
 * número que for digitado.
 */
export const SignedNumberInput = ({ value, onChange, decimals = 2, adornment, allowNegative = true, allowEmpty,
                                    onClear, placeholder, size = 'md', className, ariaLabel, error }: Props) => {
  const [pendingNegative, setPendingNegative] = useState(false);
  const negative = value != null && value !== 0 ? value < 0 : pendingNegative;
  const abs = value == null ? null : Math.abs(value);
  const toggle = () => {
    setPendingNegative(!negative);
    if (value != null && value !== 0) onChange(-value);
  };
  return (
    <div className={clsx('flex items-stretch gap-1', className)}>
      {allowNegative && (
        <button
          type="button"
          title={negative ? 'Negativo — clique para positivo' : 'Positivo — clique para negativo'}
          aria-label="Trocar sinal"
          onClick={toggle}
          className={clsx('shrink-0 rounded-md border font-semibold', size === 'sm' ? 'w-7 text-xs' : 'w-8 text-sm',
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
        allowEmpty={allowEmpty}
        onClear={() => { setPendingNegative(negative); onClear?.(); }}
        {...(placeholder !== undefined ? { placeholder } : {})}
        onPasteValue={v => {
          // colado (S2 do PLANO_USABILIDADE_PRECO_TETO): o sinal do número colado vale, se o campo aceita negativo
          const signed = allowNegative ? v : Math.abs(v);
          setPendingNegative(signed < 0 || (signed === 0 && negative));
          onChange(signed === 0 ? 0 : signed);
        }}
        onChange={v => {
          if (v === 0) setPendingNegative(negative); // apagou até zero: o sinal continua o que estava
          onChange(negative && v !== 0 ? -v : v);
        }}
        className={clsx(size === 'sm' && 'h-8 px-2 text-xs', error && 'border-danger')}
      />
    </div>
  );
};
