/**
 * Número colado num campo mascarado (docs/planos/PLANO_USABILIDADE_PRECO_TETO.md, S2): aceita o formato pt-BR
 * ("52.445.000.000,00", "-17.040.000.000,00", "12.888.732.761", "R$ 58,68", "13,75%") e também "1234.56". O sinal
 * vem de "-", "−" ou parênteses. Devolve null quando o texto não é só um número (ex.: "R$ 52,45 bi" — o abreviado
 * não é colado como se fosse o valor inteiro).
 */
export const parsePastedNumber = (text: string): number | null => {
  let t = text.trim().replace(/R\$|US\$|%/g, '').replace(/[\s\u00a0]/g, '');
  if (!t) return null;
  let negative = false;
  if (/^\(.*\)$/.test(t)) { negative = true; t = t.slice(1, -1); }
  if (/^[-−]/.test(t)) { negative = true; t = t.slice(1); } else if (t.startsWith('+')) { t = t.slice(1); }
  if (!/^[\d.,]+$/.test(t) || !/\d/.test(t)) return null;

  let normalized: string;
  if (t.includes(',')) {
    // vírgula = decimal (pt-BR); pontos = milhar
    const last = t.lastIndexOf(',');
    normalized = t.slice(0, last).replace(/[.,]/g, '') + '.' + t.slice(last + 1).replace(/[.,]/g, '');
  } else {
    const dots = (t.match(/\./g) ?? []).length;
    // vários pontos ou "12.888" (3 dígitos depois) = milhar; um ponto com outra quantidade de dígitos = decimal
    normalized = dots > 1 || /^\d{1,3}\.\d{3}$/.test(t) ? t.replace(/\./g, '') : t;
  }
  const v = Number(normalized);
  if (!Number.isFinite(v)) return null;
  return negative ? -v : v;
};
