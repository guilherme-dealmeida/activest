import React from 'react';
import { MaskedNumberInput, MaskedNumberInputProps } from './MaskedNumberInput';

type CurrencyInputProps = Omit<MaskedNumberInputProps, 'adornment'>;

/**
 * Valor em reais. Duas casas por padrão; o preço unitário de cripto (que o banco
 * guarda em NUMERIC(18,8)) passa decimals={8}, na mesma máscara.
 */
export const CurrencyInput = React.forwardRef<HTMLInputElement, CurrencyInputProps>(
  (props, ref) => <MaskedNumberInput ref={ref} adornment={{ text: 'R$', side: 'left' }} {...props} />
);

CurrencyInput.displayName = 'CurrencyInput';
