import { apiClient } from './client';
import { CodeSent, LoginRequest, SignupRequest, SignupStatus, TokenResponse } from '@/types';

/** Rotas públicas de entrada e cadastro (AuthController). */
export const authApi = {
  login: async (credentials: LoginRequest) => {
    const { data } = await apiClient.post<TokenResponse>('/auth/login', credentials);
    return data;
  },
  /**
   * credential é o ID token que o Google Identity Services entrega ao navegador.
   * O nome do campo não é livre: GoogleLoginRequestDTO espera exatamente 'credential'.
   */
  google: async (credential: string): Promise<TokenResponse> => {
    const { data } = await apiClient.post<TokenResponse>('/auth/google', { credential });
    return data;
  },
  signupStatus: async () => {
    const { data } = await apiClient.get<SignupStatus>('/auth/signup/enabled');
    return data;
  },
  signupStart: async (body: SignupRequest) => {
    const { data } = await apiClient.post<CodeSent>('/auth/signup/start', body);
    return data;
  },
  signupResend: async (requestId: string) => {
    const { data } = await apiClient.post<CodeSent>('/auth/signup/resend', { requestId });
    return data;
  },
  signupConfirm: async (requestId: string, code: string) => {
    const { data } = await apiClient.post<TokenResponse>('/auth/signup/confirm', { requestId, code });
    return data;
  },
  forgot: async (email: string) => {
    const { data } = await apiClient.post<CodeSent>('/auth/password/forgot', { email });
    return data;
  },
  forgotResend: async (requestId: string) => {
    const { data } = await apiClient.post<CodeSent>('/auth/password/resend', { requestId });
    return data;
  },
  reset: async (requestId: string, code: string, newPassword: string) => {
    await apiClient.post('/auth/password/reset', { requestId, code, newPassword });
  },
};
