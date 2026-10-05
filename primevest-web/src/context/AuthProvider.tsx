import React, { useState, useEffect, useCallback } from 'react';
import { authApi } from '@/api/auth';
import { usersApi } from '@/api/users';
import { tokens, TERMS_REQUIRED_EVENT } from '@/api/tokens';
import { TokenResponse, User } from '@/types';
import { AuthContext } from './AuthContext';
import { useTheme } from './ThemeContext';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUserState] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const { setTheme } = useTheme();

  /** O tema é do usuário (D13): chegou o usuário, a tela fica no tema dele. */
  const aplicar = useCallback((u: User) => {
    setUserState(u);
    setTheme(u.theme === 'LIGHT' ? 'light' : 'dark');
  }, [setTheme]);

  const fetchUser = useCallback(async () => {
    try {
      aplicar(await usersApi.me());
    } catch {
      tokens.clear();
      setUserState(null);
    }
  }, [aplicar]);

  useEffect(() => {
    if (tokens.access()) {
      fetchUser().finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
  }, [fetchUser]);

  // A API recusou por termos desatualizados (D19): recarrega o usuário, e o modal de aceite aparece.
  useEffect(() => {
    const onTerms = () => { if (tokens.access()) fetchUser(); };
    window.addEventListener(TERMS_REQUIRED_EVENT, onTerms);
    return () => window.removeEventListener(TERMS_REQUIRED_EVENT, onTerms);
  }, [fetchUser]);

  /**
   * Guardar os tokens e carregar o usuário são um passo só: enquanto o
   * fetchUser não volta, isAuthenticated ainda é false e as rotas protegidas
   * devolvem o usuário para /login. Todas as entradas passam por aqui.
   */
  const startSession = useCallback(async (t: TokenResponse) => {
    tokens.save(t);
    await fetchUser();
  }, [fetchUser]);

  const login = async (email: string, password: string) => {
    await startSession(await authApi.login({ email, password }));
  };

  const loginWithGoogle = async (credential: string) => {
    await startSession(await authApi.google(credential));
  };

  const logout = () => {
    tokens.clear();
    setUserState(null);
    window.location.href = '/login';
  };

  return (
    <AuthContext.Provider value={{
      user, isAuthenticated: !!user, isLoading, login, loginWithGoogle, startSession,
      setUser: aplicar, refreshUser: fetchUser, logout,
    }}>
      {children}
    </AuthContext.Provider>
  );
};
