import React, { InputHTMLAttributes } from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { LabelWithInfo } from './InfoTooltip';
import { maskedChange, maskedDisplay, maskedPaste } from '@/utils/maskedNumber';

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
  /**
   * Número colado, já com o sinal (o campo só mostra o valor absoluto). Sem isso, colar chama {@link onChange} com o
   * valor absoluto. Quem tem o botão ± ({@link SignedNumberInput}) usa para ligar o sinal.
   */
  onPasteValue?: (value: number) => void;
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
  ({ className, label, error, hint, info, value, onChange, decimals = 2, adornment, allowEmpty = false, onClear,
     onPasteValue, id, disabled, ...props }, ref) => {
    // O useId e chamado incondicionalmente: encurtar com `id || useId()` mudava
    // a quantidade de hooks conforme a prop, o que quebra a ordem entre renders.
    const generatedId = React.useId();
    const inputId = id ?? generatedId;

    // A máscara cresce pela direita, então o cursor só faz sentido no fim — mas só quando não há seleção: mover o
    // cursor a cada clique desfazia o arraste, o duplo e o triplo clique, e não deixava copiar o valor
    // (docs/planos/PLANO_USABILIDADE_PRECO_TETO.md, S1). O foco pelo mouse não mexe no cursor (o clique que vem
    // depois decide); o foco pelo teclado (Tab) leva ao fim.
    const pointerDown = React.useRef(false);
    const collapsed = (el: HTMLInputElement) => el.selectionStart === el.selectionEnd;
    // A conferência é no quadro seguinte: no instante do clique o navegador ainda informa a seleção anterior.
    const caretToEnd = (el: HTMLInputElement) => {
      requestAnimationFrame(() => {
        if (document.activeElement === el && collapsed(el)) el.setSelectionRange(el.value.length, el.value.length);
      });
    };
    const handleMouseDown = () => { pointerDown.current = true; };
    const handleFocus = (e: React.FocusEvent<HTMLInputElement>) => {
      if (pointerDown.current) return;
      caretToEnd(e.currentTarget);
    };
    const handleClick = (e: React.MouseEvent<HTMLInputElement>) => {
      pointerDown.current = false;
      caretToEnd(e.currentTarget);
    };
    const handleBlur = () => { pointerDown.current = false; };

    // Colar substitui o valor (S2): a máscara lê só dígitos e somava o colado ao que já estava no campo, e perdia o
    // sinal. Texto que não é número não muda nada.
    const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
      e.preventDefault();
      // regra em utils/maskedNumber.ts (testada): com sinal, nas casas do campo, até o teto de 15 dígitos
      const signed = maskedPaste(e.clipboardData.getData('text'), decimals);
      if (signed === null) return;
      if (onPasteValue) onPasteValue(signed);
      else onChange(Math.abs(signed));
    };

    const display = maskedDisplay(value, decimals, allowEmpty);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      // regra em utils/maskedNumber.ts (testada): pela direita, até 15 dígitos; com allowEmpty, apagar esvazia
      const change = maskedChange(e.target.value, decimals, value, display, allowEmpty);
      if (change.kind === 'clear') {
        onClear?.();
        return;
      }
      onChange(change.value);
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
            onMouseDown={handleMouseDown}
            onFocus={handleFocus}
            onClick={handleClick}
            onBlur={handleBlur}
            onPaste={handlePaste}
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
