import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { TokenResponse } from '@/types';
import { tokens, TERMS_REQUIRED_EVENT } from './tokens';

export const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config) => {
    const token = tokens.access();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

/**
 * Renovação do token (docs/planos/PLANO_CADASTRO_USUARIO.md, U1). Várias chamadas podem receber 401
 * ao mesmo tempo quando o token vence: todas esperam a mesma renovação, e cada uma é repetida uma
 * vez com o token novo. Sem renovação possível (refresh vencido, senha trocada em outro lugar), a
 * sessão acaba e a tela volta para /login.
 */
let renovacao: Promise<string | null> | null = null;

const renovar = async (): Promise<string | null> => {
  const refreshToken = tokens.refresh();
  if (!refreshToken) return null;
  try {
    // axios "cru": sem os interceptors, para um 401 aqui não entrar em laço
    const { data } = await axios.post<TokenResponse>('/api/v1/auth/refresh', { refreshToken });
    tokens.save(data);
    return data.accessToken;
  } catch {
    return null;
  }
};

type RetryConfig = InternalAxiosRequestConfig & { _renovado?: boolean };

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<{ errors?: Record<string, string> }>) => {
    const config = error.config as RetryConfig | undefined;
    // 401 numa rota de entrada é credencial recusada, não sessão vencida: não
    // há token a limpar, e recarregar /login apagaria a mensagem de erro antes
    // de o usuário lê-la.
    const isAuthRequest = String(config?.url ?? '').startsWith('/auth/');

    if (error.response?.status === 401 && !isAuthRequest && config && !config._renovado) {
      config._renovado = true;
      renovacao ??= renovar().finally(() => { renovacao = null; });
      const novo = await renovacao;
      if (novo) {
        config.headers.Authorization = `Bearer ${novo}`;
        return apiClient(config);
      }
      tokens.clear();
      window.location.href = '/login';
    }

    // Termos atualizados (D19): a tela abre o modal de novo aceite.
    if (error.response?.status === 403 && error.response.data?.errors?.code === 'TERMS_REQUIRED') {
      window.dispatchEvent(new Event(TERMS_REQUIRED_EVENT));
    }
    return Promise.reject(error);
  }
);
