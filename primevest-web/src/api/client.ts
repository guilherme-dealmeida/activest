import axios from 'axios';

export const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('primevest_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // 401 numa rota de entrada é credencial recusada, não sessão vencida: não
    // há token a limpar, e recarregar /login apagaria a mensagem de erro antes
    // de o usuário lê-la.
    const isAuthRequest = String(error.config?.url ?? '').startsWith('/auth/');
    if (error.response?.status === 401 && !isAuthRequest) {
      localStorage.removeItem('primevest_token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
