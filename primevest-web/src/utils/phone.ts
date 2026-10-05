/** Máscara de celular brasileiro enquanto digita: (41) 99999-9999. */
export const formatPhone = (value: string): string => {
  const d = value.replace(/\D/g, '').slice(0, 11);
  if (d.length === 0) return '';
  if (d.length <= 2) return `(${d}`;
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
};

/** E.164 da API (+5541999999999) para a tela; número de fora do Brasil fica como veio. */
export const phoneFromApi = (e164?: string | null): string => {
  if (!e164) return '';
  return e164.startsWith('+55') ? formatPhone(e164.slice(3)) : e164;
};

/** Vazio ou com 10/11 dígitos (DDD + número). */
export const isPhoneComplete = (value: string): boolean => {
  const n = value.replace(/\D/g, '').length;
  return n === 0 || n === 10 || n === 11;
};
