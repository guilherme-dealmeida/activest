import React, { InputHTMLAttributes } from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { LabelWithInfo } from './InfoTooltip';

export interface MaskedNumberInputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type'> {
  label?: string;
  error?: string;
  /** Texto auxiliar abaixo do campo. Suprimido quando há erro. */
  hint?: string;
  /** Explicação no (i) ao lado do rótulo, no lugar de texto solto na tela. */
  info?: React.ReactNode;
  value: number | null | undefined;
  onChange: (value: number) => void;
  /** Casas decimais fixas da máscara. */
  decimals?: number;
  /** Símbolo colado ao campo: 'R$' à esquerda, '%' à direita. */
  adornment?: { text: string; side: 'left' | 'right' };
  /**
   * Campo que pode ficar vazio (filtros): `value` nulo mostra o campo vazio, o zero aparece como "0,00" e apagar
   * tudo chama {@link onClear} em vez de mandar 0. Sem isso, vazio e zero são a mesma coisa — o que serve aos
   * formulários, onde o campo vazio vale zero, mas não a um filtro, onde vazio é "sem filtro" e zero é um limite.
   */
  allowEmpty?: boolean;
  /** Com {@link allowEmpty}: o usuário apagou o campo. */
  onClear?: () => void;
}

/**
 * Campo numérico com máscara sempre ativa.
 *
 * A digitação é lida da DIREITA para a esquerda, na menor fração: com duas
 * casas, 1 -> 0,01, 10 -> 0,10, 1050 -> 10,50. O usuário nunca digita
 * separador, o que elimina a ambiguidade entre ponto e vírgula — quem está
 * acostumado a teclar "1.5" esperando um e meio não recebe quinze.
 *
 * O estado real é sempre o number recebido em value; a string formatada é
 * derivada dele a cada render, e não guardada à parte. Enquanto havia dois
 * estados (o número e o texto), eles saíam de sincronia ao editar um registro
 * ou ao limpar o formulário.
 *
 * CurrencyInput e PercentInput são as duas faces disso. Não duplique a
 * máscara: se precisar de outra unidade, embrulhe este componente.
 */
export const MaskedNumberInput = React.forwardRef<HTMLInputElement, MaskedNumberInputProps>(
  ({ className, label, error, hint, info, value, onChange, decimals = 2, adornment, allowEmpty = false, onClear, id,
     disabled, ...props }, ref) => {
    // O useId e chamado incondicionalmente: encurtar com `id || useId()` mudava
    // a quantidade de hooks conforme a prop, o que quebra a ordem entre renders.
    const generatedId = React.useId();
    const inputId = id ?? generatedId;

    // A máscara cresce pela direita, então o cursor só faz sentido no fim.
    const caretToEnd = (e: React.FocusEvent<HTMLInputElement> | React.MouseEvent<HTMLInputElement>) => {
      const el = e.currentTarget;
      requestAnimationFrame(() => el.setSelectionRange(el.value.length, el.value.length));
    };

    const shown = allowEmpty ? value !== null && value !== undefined : !!value;
    const display = shown
      ? (value as number).toLocaleString('pt-BR', {
          minimumFractionDigits: decimals,
          maximumFractionDigits: decimals,
        })
      : '';

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      // 15 dígitos cobrem NUMERIC(18,2) com folga e evitam estourar o Number.
      // Com 8 casas (quantidade de cripto) o teto é 9.999.999,99999999.
      const digits = e.target.value.replace(/\D/g, '').slice(0, 15);
      // Com allowEmpty: apagar tudo esvazia; apagar de novo um campo que já está em zero também (sem isso o
      // backspace num "0,00" volta sempre para "0,00").
      const deletingZero = Number(digits) === 0 && value === 0 && e.target.value.length < display.length;
      if (allowEmpty && (!digits || deletingZero)) {
        onClear?.();
        return;
      }
      onChange(digits ? Number(digits) / 10 ** decimals : 0);
    };

    const left = adornment?.side === 'left';
    const right = adornment?.side === 'right';

    return (
      <div className="w-full">
        {label && (
          <label htmlFor={inputId} className="block text-sm font-medium text-gray-300 mb-1.5">
            <LabelWithInfo info={info}>{label}</LabelWithInfo>
          </label>
        )}
        <div className="relative">
          {adornment && (
            <span
              className={clsx(
                'absolute top-1/2 -translate-y-1/2 text-sm pointer-events-none',
                left && 'left-3',
                right && 'right-3',
                disabled ? 'text-gray-600' : 'text-gray-400'
              )}
            >
              {adornment.text}
            </span>
          )}
          <input
            id={inputId}
            ref={ref}
            type="text"
            inputMode="numeric"
            disabled={disabled}
            value={display}
            onChange={handleChange}
            onFocus={caretToEnd}
            onClick={caretToEnd}
            placeholder={(0).toFixed(decimals).replace('.', ',')}
            className={twMerge(
              clsx(
                'flex h-10 w-full rounded-md border border-surface-light bg-surface py-2 text-sm text-white text-right tabular-nums placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:ring-accent focus:border-transparent disabled:cursor-not-allowed disabled:opacity-50 transition-colors',
                left ? 'pl-9 pr-3' : right ? 'pl-3 pr-8' : 'px-3',
                error && 'border-danger focus:ring-danger',
                className
              )
            )}
            {...props}
          />
        </div>
        {error && <p className="mt-1 text-sm text-danger">{error}</p>}
        {!error && hint && <p className="mt-1 text-xs text-gray-500">{hint}</p>}
      </div>
    );
  }
);

MaskedNumberInput.displayName = 'MaskedNumberInput';
