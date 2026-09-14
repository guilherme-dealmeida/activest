import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authApi } from '@/api/auth';
import { apiClient } from '@/api/client';

interface User {
  id: string;
  name: string;
  email: string;
  avatarUrl?: string;
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  /** Login pelo Google: recebe o ID token que o Google entregou ao navegador. */
  loginWithGoogle: (credential: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const fetchUser = useCallback(async () => {
    try {
      const { data } = await apiClient.get('/users/me');
      setUser(data);
    } catch {
      localStorage.removeItem('primevest_token');
      setUser(null);
    }
  }, []);

  useEffect(() => {
    const token = localStorage.getItem('primevest_token');
    if (token) {
      fetchUser().finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
  }, [fetchUser]);

  const login = async (email: string, password: string) => {
    const response = await authApi.login({ email, password });
    await establishSession(response.accessToken);
  };

  const loginWithGoogle = async (credential: string) => {
    const response = await authApi.google(credential);
    await establishSession(response.accessToken);
  };

  /**
   * Guardar o token e carregar o usuário são um passo só: enquanto o
   * fetchUser não volta, isAuthenticated ainda é false e as rotas protegidas
   * devolvem o usuário para /login. Os dois logins passam por aqui para não
   * divergirem nessa ordem.
   */
  const establishSession = async (accessToken: string) => {
    localStorage.setItem('primevest_token', accessToken);
    await fetchUser();
  };

  const logout = () => {
    localStorage.removeItem('primevest_token');
    setUser(null);
    window.location.href = '/login';
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, isLoading, login, loginWithGoogle, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
