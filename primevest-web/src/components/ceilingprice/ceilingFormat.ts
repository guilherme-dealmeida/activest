import { CeilingStatus } from '@/types';

/** Texto do (i) do Preço Ideal — tela do cálculo e cabeçalho da lista. */
export const IDEAL_PRICE_INFO =
  'Preço Teto com a margem de segurança aplicada.';

export const STATUS_LABEL: Record<CeilingStatus, string> = {
  ABAIXO_DO_IDEAL: 'Abaixo do ideal',
  ENTRE_IDEAL_E_TETO: 'Entre ideal e teto',
  ACIMA_DO_TETO: 'Acima do teto',
};

export const STATUS_VARIANT: Record<CeilingStatus, 'success' | 'warning' | 'danger' | 'default'> = {
  ABAIXO_DO_IDEAL: 'success',
  ENTRE_IDEAL_E_TETO: 'warning',
  ACIMA_DO_TETO: 'danger',
};

/** Percentual em pontos com sinal: +22,22% / −3,10%. */
export const signedPct = (v: number | null | undefined, digits = 2) => {
  if (v == null) return '—';
  const s = Math.abs(v).toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  return `${v > 0 ? '+' : v < 0 ? '−' : ''}${s}%`;
};

/** Percentual em pontos sem sinal forçado. */
export const pct = (v: number | null | undefined, digits = 2) =>
  v == null ? '—' : `${v.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits })}%`;

/** Valor grande em reais, abreviado: R$ 10,37 bi. */
export const bigMoney = (v: number | null | undefined) => {
  if (v == null) return '—';
  const abs = Math.abs(v);
  const sign = v < 0 ? '−' : '';
  const fmt = (n: number, suf: string) =>
    `${sign}R$ ${n.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}${suf}`;
  if (abs >= 1e9) return fmt(abs / 1e9, ' bi');
  if (abs >= 1e6) return fmt(abs / 1e6, ' mi');
  return fmt(abs, '');
};

