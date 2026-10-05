import { TokenResponse } from '@/types';

/**
 * Onde a sessão fica no navegador. O token de acesso vale 24 h; o de renovação, 7 dias — o
 * interceptor do api/client.ts troca um pelo outro quando a API responde 401.
 */
const ACCESS_KEY = 'primevest_token';
const REFRESH_KEY = 'primevest_refresh_token';

const ler = (key: string): string | null => {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
};

export const tokens = {
  access: () => ler(ACCESS_KEY),
  refresh: () => ler(REFRESH_KEY),
  save: (t: Pick<TokenResponse, 'accessToken' | 'refreshToken'>) => {
    try {
      localStorage.setItem(ACCESS_KEY, t.accessToken);
      if (t.refreshToken) localStorage.setItem(REFRESH_KEY, t.refreshToken);
    } catch {
      // sem armazenamento a sessão vale só enquanto a aba estiver aberta
    }
  },
  clear: () => {
    try {
      localStorage.removeItem(ACCESS_KEY);
      localStorage.removeItem(REFRESH_KEY);
    } catch {
      // nada a limpar
    }
  },
};

/** Evento que o client dispara quando a API pede novo aceite dos termos (403 TERMS_REQUIRED, D19). */
export const TERMS_REQUIRED_EVENT = 'primevest:terms-required';
