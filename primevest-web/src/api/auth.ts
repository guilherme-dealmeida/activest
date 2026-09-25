import { apiClient } from './client';
import { LoginRequest, RegisterRequest, TokenResponse } from '@/types';

export const authApi = {
  login: async (credentials: LoginRequest) => {
    const { data } = await apiClient.post<TokenResponse>('/auth/login', credentials);
    return data;
  },
  register: async (userData: RegisterRequest) => {
    const { data } = await apiClient.post<TokenResponse>('/auth/register', userData);
    return data;
  },
  /**
   * credential é o ID token que o Google Identity Services entrega ao navegador.
   * O nome do campo não é livre: GoogleLoginRequestDTO espera exatamente
   * 'credential'. Antes daqui saía { token }, que chegava nulo do outro lado e
   * morria na validação @NotBlank.
   */
  google: async (credential: string): Promise<TokenResponse> => {
    const { data } = await apiClient.post<TokenResponse>('/auth/google', { credential });
    return data;
  }
};
