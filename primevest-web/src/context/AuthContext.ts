import { createContext, useContext } from 'react';

// O provider fica em AuthProvider.tsx: arquivo que exporta componente e hook
// junto perde o fast refresh do Vite (react-refresh/only-export-components).

export interface User {
  id: string;
  name: string;
  email: string;
  avatarUrl?: string;
}

export interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  /** Login pelo Google: recebe o ID token que o Google entregou ao navegador. */
  loginWithGoogle: (credential: string) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
