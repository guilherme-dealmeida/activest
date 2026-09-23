import { createContext, useContext } from 'react';

// O provider fica em ToastProvider.tsx (fast refresh do Vite).

/**
 * Avisos curtos de resultado de ação, no canto superior direito.
 *
 * O projeto não tinha nenhum canal para "deu certo": erro aparecia dentro do
 * formulário que o causou, e sucesso não aparecia em lugar nenhum. Um switch
 * que grava direto, sem modal para fechar, não tem onde pendurar essa
 * resposta — daí o toast ser global e não um banner de uma tela só.
 *
 * Empilha, some sozinho e pode ser fechado no X. O timer vive num ref por id,
 * e não num useEffect por toast, para que fechar no X também cancele o
 * agendamento em vez de deixá-lo disparar num id que já saiu.
 */
export type ToastVariant = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  message: string;
  variant: ToastVariant;
}

export interface ToastContextType {
  /**
   * Mostra um aviso. O padrão dura 4s — 8s no erro —; 0 deixa o aviso até o
   * usuário fechar.
   */
  showToast: (message: string, variant?: ToastVariant, durationMs?: number) => void;
}

export const ToastContext = createContext<ToastContextType>({
  showToast: () => {},
});

export const useToast = () => useContext(ToastContext);
