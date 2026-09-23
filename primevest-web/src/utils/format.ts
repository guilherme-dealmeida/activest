export const formatCurrency = (value: number): string => {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
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

export const getReturnColor = (value: number): string => {
  if (value > 0) return 'text-success';
  if (value < 0) return 'text-danger';
  return 'text-gray-400';
};
