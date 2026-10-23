import { createContext, useContext } from 'react';
import { TokenResponse, User } from '@/types';

// O provider fica em AuthProvider.tsx: arquivo que exporta componente e hook
// junto perde o fast refresh do Vite (react-refresh/only-export-components).

export type { User };

export interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  /** Login pelo Google: recebe o ID token que o Google entregou ao navegador. */
  loginWithGoogle: (credential: string) => Promise<void>;
  /** Entra com tokens já emitidos (fim do cadastro, troca de senha). */
  startSession: (tokens: TokenResponse) => Promise<void>;
  /** Troca o usuário em memória pelo que a API devolveu (depois de salvar em Configurações). */
  setUser: (user: User) => void;
  refreshUser: () => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
