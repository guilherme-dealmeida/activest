import { describe, expect, it } from 'vitest';
import { maskedChange, maskedDisplay, maskedPaste } from './maskedNumber';
import { parsePastedNumber } from './numberPaste';

const typed = (text: string, decimals = 2, current: number | null = 0, display = '', allowEmpty = false) =>
  maskedChange(text, decimals, current, display, allowEmpty);

describe('digitação pela direita', () => {
  it('na menor fração: 1 → 0,01; 10 → 0,10; 1050 → 10,50', () => {
    expect(typed('1')).toEqual({ kind: 'value', value: 0.01 });
    expect(typed('10')).toEqual({ kind: 'value', value: 0.1 });
    expect(typed('1050')).toEqual({ kind: 'value', value: 10.5 });
  });

  it('a tecla digitada entra no fim do texto mostrado', () => {
    // "10,50" + "7" → 105,07
    expect(typed('10,507')).toEqual({ kind: 'value', value: 105.07 });
  });

  it('com 8 casas (cripto)', () => {
    expect(typed('197802', 8)).toEqual({ kind: 'value', value: 0.00197802 });
  });

  it('teto de 15 dígitos: o excedente é ignorado', () => {
    expect(typed('1234567890123456789')).toEqual({ kind: 'value', value: 1234567890123.45 });
  });

  it('sem dígitos: zero (campo sem allowEmpty)', () => {
    expect(typed('')).toEqual({ kind: 'value', value: 0 });
  });

  it('allowEmpty: apagar tudo esvazia', () => {
    expect(typed('', 2, 5, '0,05', true)).toEqual({ kind: 'clear' });
  });

  it('allowEmpty: backspace num "0,00" esvazia em vez de voltar a "0,00"', () => {
    expect(typed('0,0', 2, 0, '0,00', true)).toEqual({ kind: 'clear' });
    // digitar zero num campo em zero não esvazia (o texto cresceu)
    expect(typed('0,000', 2, 0, '0,00', true)).toEqual({ kind: 'value', value: 0 });
  });
});

describe('texto mostrado', () => {
  it('pt-BR com as casas fixas', () => {
    expect(maskedDisplay(1234.5, 2, false)).toBe('1.234,50');
    expect(maskedDisplay(0.00197802, 8, false)).toBe('0,00197802');
  });

  it('zero: vazio sem allowEmpty, "0,00" com allowEmpty; nulo vazio', () => {
    expect(maskedDisplay(0, 2, false)).toBe('');
    expect(maskedDisplay(0, 2, true)).toBe('0,00');
    expect(maskedDisplay(null, 2, true)).toBe('');
  });
});

describe('colar', () => {
  it('substitui o valor, com o sinal', () => {
    expect(maskedPaste('15.563.000.000,00', 2)).toBe(15563000000);
    expect(maskedPaste('-17.040.000.000,00', 2)).toBe(-17040000000);
    expect(maskedPaste('(1.234,56)', 2)).toBe(-1234.56);
  });

  it('arredonda às casas do campo', () => {
    expect(maskedPaste('58,6789', 2)).toBe(58.68);
    expect(maskedPaste('0,123456789', 8)).toBe(0.12345679);
  });

  it('acima do teto de 15 dígitos não muda nada', () => {
    expect(maskedPaste('99.999.999.999.999,99', 2)).toBeNull();
    expect(maskedPaste('9.999.999.999.999,99', 2)).toBe(9999999999999.99);
  });

  it('texto que não é só número não muda nada', () => {
    expect(maskedPaste('R$ 52,45 bi', 2)).toBeNull();
    expect(maskedPaste('abc', 2)).toBeNull();
  });
});

describe('parsePastedNumber', () => {
  it('formatos pt-BR, com símbolo e percentual', () => {
    expect(parsePastedNumber('52.445.000.000,00')).toBe(52445000000);
    expect(parsePastedNumber('R$ 58,68')).toBe(58.68);
    expect(parsePastedNumber('US$ 1.234,56')).toBe(1234.56);
    expect(parsePastedNumber('13,75%')).toBe(13.75);
    expect(parsePastedNumber(' 1 234,5 ')).toBe(1234.5);
  });

  it('sinal por "-", "−" ou parênteses; "+" aceito', () => {
    expect(parsePastedNumber('−3,2')).toBe(-3.2);
    expect(parsePastedNumber('(10)')).toBe(-10);
    expect(parsePastedNumber('+7')).toBe(7);
  });

  it('formato americano e o caso ambíguo "12.888" (milhar)', () => {
    expect(parsePastedNumber('1234.56')).toBe(1234.56);
    expect(parsePastedNumber('12.888')).toBe(12888);
    expect(parsePastedNumber('12.888.732.761')).toBe(12888732761);
    expect(parsePastedNumber('12.5')).toBe(12.5);
  });

  it('o que não é número: nulo', () => {
    expect(parsePastedNumber('')).toBeNull();
    expect(parsePastedNumber('R$ 52,45 bi')).toBeNull();
    expect(parsePastedNumber('1,2,3x')).toBeNull();
  });
});
