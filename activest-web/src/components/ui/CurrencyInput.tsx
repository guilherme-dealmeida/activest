import React from 'react';
import { MaskedNumberInput, MaskedNumberInputProps } from './MaskedNumberInput';

type CurrencyInputProps = Omit<MaskedNumberInputProps, 'adornment'> & {
  /**
   * Símbolo da moeda do ativo (docs/planos/PLANO_CADASTRO_MOEDAS.md): "US$" num ativo cotado em dólar. Vem do ativo,
   * nunca de uma escolha do usuário. Padrão: R$.
   */
  symbol?: string;
};

/**
 * Valor em dinheiro, em reais por padrão. Duas casas por padrão; o preço unitário de cripto (que o banco
 * guarda em NUMERIC(18,8)) passa decimals={8}, na mesma máscara.
 */
export const CurrencyInput = React.forwardRef<HTMLInputElement, CurrencyInputProps>(
  ({ symbol = 'R$', ...props }, ref) => <MaskedNumberInput ref={ref} adornment={{ text: symbol, side: 'left' }} {...props} />
);

CurrencyInput.displayName = 'CurrencyInput';
