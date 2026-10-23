import { createContext, useContext } from 'react';

// O provider fica em ThemeProvider.tsx (fast refresh do Vite).

export type Theme = 'dark' | 'light';

export interface ThemeContextType {
  theme: Theme;
  /** Troca o tema e, com alguém logado, grava no usuário (D13). */
  toggleTheme: () => void;
  /** Aplica um tema sem gravar — o AuthProvider usa ao carregar o usuário. */
  setTheme: (theme: Theme) => void;
  isDark: boolean;
}

export const ThemeContext = createContext<ThemeContextType>({
  theme: 'dark',
  toggleTheme: () => {},
  setTheme: () => {},
  isDark: true,
});

export const useTheme = () => useContext(ThemeContext);
