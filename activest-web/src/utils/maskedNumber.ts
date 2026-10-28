import { parsePastedNumber } from './numberPaste';

/**
 * Regras do campo numérico mascarado (`MaskedNumberInput`, base de `CurrencyInput`, `PercentInput` e
 * `SignedNumberInput`) — docs/planos/PLANO_TESTES_FRONT.md, 40.6. Testado em `maskedNumber.test.ts`.
 *
 * A digitação é lida da DIREITA para a esquerda, na menor fração: com duas casas, 1 → 0,01, 10 → 0,10,
 * 1050 → 10,50. O usuário nunca digita separador.
 */

/** 15 dígitos cobrem NUMERIC(18,2) com folga e não estouram o Number; com 8 casas, até 9.999.999,99999999. */
export const MAX_DIGITS = 15;

/** Texto mostrado: o número em pt-BR com as casas fixas; vazio quando o campo não mostra valor. */
export const maskedDisplay = (value: number | null | undefined, decimals: number, allowEmpty: boolean): string => {
  const shown = allowEmpty ? value !== null && value !== undefined : !!value;
  return shown
    ? (value as number).toLocaleString('pt-BR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
    : '';
};

/** Resultado de uma digitação: o valor novo, ou `clear` (com `allowEmpty`, o campo fica vazio). */
export type MaskedChange = { kind: 'value'; value: number } | { kind: 'clear' };

/**
 * Digitação no campo. `typed` = o texto do campo depois da tecla; `display` = o texto antes dela.
 * Com `allowEmpty`: apagar tudo esvazia; apagar de novo um campo que já está em zero também (sem isso o backspace
 * num "0,00" voltava sempre para "0,00").
 */
export const maskedChange = (typed: string, decimals: number, current: number | null | undefined, display: string,
  allowEmpty: boolean): MaskedChange => {
  const digits = typed.replace(/\D/g, '').slice(0, MAX_DIGITS);
  const deletingZero = Number(digits) === 0 && current === 0 && typed.length < display.length;
  if (allowEmpty && (!digits || deletingZero)) return { kind: 'clear' };
  return { kind: 'value', value: digits ? Number(digits) / 10 ** decimals : 0 };
};

/**
 * Colar (docs/planos/PLANO_USABILIDADE_PRECO_TETO.md, S2): o colado substitui o valor, com sinal, arredondado às casas
 * do campo. Nulo (não muda nada) quando o texto não é só um número ou passa do teto de 15 dígitos.
 */
export const maskedPaste = (text: string, decimals: number): number | null => {
  const parsed = parsePastedNumber(text);
  if (parsed === null) return null;
  const scale = 10 ** decimals;
  const signed = Math.round(parsed * scale) / scale;
  if (Math.round(Math.abs(signed) * scale) > 999_999_999_999_999) return null;
  return signed;
};
