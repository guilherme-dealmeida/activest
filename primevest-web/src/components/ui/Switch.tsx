import { clsx } from 'clsx';

interface SwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  /** Descrição para leitor de tela — a chave não tem rótulo visível na tabela. */
  label: string;
  title?: string;
}

/**
 * Chave liga/desliga.
 *
 * role="switch" com aria-checked, e não uma caixa de seleção estilizada: o
 * estado é aplicado na hora, sem formulário para confirmar, e é isso que o
 * switch comunica.
 */
export const Switch = ({ checked, onChange, disabled = false, label, title }: SwitchProps) => (
  <button
    type="button"
    role="switch"
    aria-checked={checked}
    aria-label={label}
    title={title ?? label}
    disabled={disabled}
    onClick={() => onChange(!checked)}
    className={clsx(
      'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full border transition-colors',
      'focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60',
      checked ? 'border-accent/60 bg-accent/70' : 'border-white/15 bg-surface-light',
      disabled && 'cursor-not-allowed opacity-50'
    )}
  >
    <span
      className={clsx(
        'inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow transition-transform',
        checked ? 'translate-x-[18px]' : 'translate-x-[3px]'
      )}
    />
  </button>
);
