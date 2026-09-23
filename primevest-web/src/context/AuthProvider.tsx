import React, { useState, useEffect, useCallback } from 'react';
import { authApi } from '@/api/auth';
import { apiClient } from '@/api/client';
import { AuthContext, User } from './AuthContext';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const fetchUser = useCallback(async () => {
    try {
      const { data } = await apiClient.get<User>('/users/me');
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
