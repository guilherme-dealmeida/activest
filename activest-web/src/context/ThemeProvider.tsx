import { useState, useEffect, ReactNode, useCallback } from 'react';
import { ThemeContext, Theme } from './ThemeContext';
import { tokens } from '@/api/tokens';
import { usersApi } from '@/api/users';

const STORAGE_KEY = 'activest-theme';

/**
 * Tema da tela. Desde 05/10/2026 é do usuário, no servidor (docs/planos/PLANO_CADASTRO_USUARIO.md,
 * D13): o AuthProvider aplica o do usuário ao carregá-lo, e trocar aqui grava nele. O localStorage
 * ficou só como cache — é o que pinta a tela de login e o primeiro desenho antes do usuário chegar.
 */
export const ThemeProvider = ({ children }: { children: ReactNode }) => {
  const [theme, setThemeState] = useState<Theme>(() => {
    try {
      return (localStorage.getItem(STORAGE_KEY) as Theme) || 'dark';
    } catch {
      return 'dark';
    }
  });

  const isDark = theme === 'dark';

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // sem armazenamento, o tema vale para esta visita
    }
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
      root.classList.remove('light');
    } else {
      root.classList.add('light');
      root.classList.remove('dark');
    }
  }, [theme]);

  const setTheme = useCallback((t: Theme) => setThemeState(t), []);

  const toggleTheme = () => {
    const created: Theme = theme === 'dark' ? 'light' : 'dark';
    setThemeState(created);
    if (tokens.access()) {
      // Falhar aqui não desfaz a troca na tela: o tema só não acompanha o usuário em outro navegador.
      usersApi.preferences(created === 'dark' ? 'DARK' : 'LIGHT').catch(() => {});
    }
  };

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme, setTheme, isDark }}>
      {children}
    </ThemeContext.Provider>
  );
};
