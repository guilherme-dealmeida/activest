export const formatCurrency = (value: number): string => {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(value);
};

/**
 * Valor na moeda de um ativo de fora (docs/planos/PLANO_CADASTRO_MOEDAS.md): o símbolo do cadastro ("US$ 1.234,56"),
 * no formato brasileiro. Os valores em reais continuam com formatCurrency.
 */
export const formatMoney = (value: number, symbol: string, minDigits = 2, maxDigits = 2): string => {
  const n = new Intl.NumberFormat('pt-BR', {
    minimumFractionDigits: minDigits,
    maximumFractionDigits: Math.max(minDigits, maxDigits),
  }).format(Math.abs(value));
  return `${value < 0 ? '-' : ''}${symbol}\u00a0${n}`;
};

/** Câmbio com 4 casas ("5,4321"), sem símbolo. */
export const formatFxRate = (value: number): string =>
  new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 4, maximumFractionDigits: 8 }).format(value);

/**
 * Moeda com casas fixas — valor por cota de provento, que a B3 publica com 8+
 * casas (R$ 0,47160378). Só exibição: a conta usa o valor exato.
 */
export const formatCurrencyPrecise = (value: number, digits = 8): string => {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(value);
};

export const formatPercentage = (value: number): string => {
  return new Intl.NumberFormat('pt-BR', {
    style: 'percent',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value / 100);
};

/**
 * Formata datas para pt-BR sem sofrer deslocamento de fuso.
 * Strings "YYYY-MM-DD" (LocalDate do backend) são interpretadas pelo `new Date()`
 * como meia-noite UTC, o que no Brasil (UTC-3) vira o dia anterior — por isso
 * são tratadas manualmente.
 */
export const formatDate = (dateStr: string): string => {
  if (!dateStr) return '';
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateStr);
  if (match) {
    const [, year, month, day] = match;
    return `${day}/${month}/${year}`;
  }
  return new Intl.DateTimeFormat('pt-BR').format(new Date(dateStr));
};

/** Retorna a data no fuso local no formato "YYYY-MM-DD" (sem conversão para UTC). */
export const toLocalISODate = (date: Date = new Date()): string => {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
};

export const formatShares = (value: number): string => {
  return new Intl.NumberFormat('pt-BR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 4,
  }).format(value);
};

/**
 * Quantidade conforme o tipo: cripto com até 8 casas e sem zeros sobrando
 * (0,00197802), os demais como sempre (até 4).
 */
export const formatQuantity = (value: number, crypto: boolean): string => {
  if (!crypto) return formatShares(value);
  return new Intl.NumberFormat('pt-BR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 8,
  }).format(value);
};

/**
 * Preço unitário conforme o tipo: cripto com 2 a 8 casas (R$ 503.233,00,
 * R$ 0,00007123), os demais com 2. Só exibição: a conta usa o valor exato.
 */
export const formatUnitPrice = (value: number, crypto: boolean): string => {
  if (!crypto) return formatCurrency(value);
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
    maximumFractionDigits: 8,
  }).format(value);
};

export const getReturnColor = (value: number): string => {
  if (value > 0) return 'text-success';
  if (value < 0) return 'text-danger';
  return 'text-gray-400';
};
