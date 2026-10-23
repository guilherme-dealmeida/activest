import { useCallback, useMemo, useRef, useState, ReactNode } from 'react';
import { clsx } from 'clsx';
import { AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import { ToastContext, Toast, ToastVariant } from './ToastContext';

const DEFAULT_DURATION_MS = 4000;

/**
 * Erro fica o dobro. As mensagens de regra do backend são frases inteiras ("A
 * venda de Tesouro Direto é do ativo…"), e desde que o erro do formulário de
 * transação passou a sair só no toast, sumir em 4s era perder a explicação.
 */
const ERROR_DURATION_MS = 8000;

const VARIANT_STYLES: Record<ToastVariant, string> = {
  success: 'border-success/40 bg-success/15 text-success',
  error: 'border-danger/40 bg-danger/15 text-danger',
  info: 'border-accent/40 bg-accent/15 text-accent',
};

const VARIANT_ICONS: Record<ToastVariant, typeof Info> = {
  success: CheckCircle2,
  error: AlertTriangle,
  info: Info,
};

export const ToastProvider = ({ children }: { children: ReactNode }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  const dismiss = useCallback((id: number) => {
    const timer = timers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timers.current.delete(id);
    }
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback(
    (message: string, variant: ToastVariant = 'info', durationMs?: number) => {
      const duration = durationMs ?? (variant === 'error' ? ERROR_DURATION_MS : DEFAULT_DURATION_MS);
      const id = nextId.current++;
      setToasts((prev) => [...prev, { id, message, variant }]);
      if (duration > 0) {
        timers.current.set(id, setTimeout(() => dismiss(id), duration));
      }
    },
    [dismiss]
  );

  // O valor do contexto não pode ser recriado a cada render: ele é lido por
  // toda tela protegida, e uma referência nova remontaria todas elas.
  const value = useMemo(() => ({ showToast }), [showToast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        // aria-live para que o aviso seja anunciado: ele aparece longe de onde
        // o usuário clicou, e um leitor de tela não olha para o canto da tela.
        aria-live="polite"
        aria-atomic="false"
        // z-[70]: acima do Modal (z-50) e do balão do InfoTooltip (z-[60]).
        // Com z-50 o aviso ficava atrás do fundo escurecido do modal, porque o
        // portal do modal entra depois no DOM — e desde 23/09/2026 o erro do
        // formulário de transação sai aqui, com o modal ainda aberto.
        className="pointer-events-none fixed top-4 right-4 z-[70] flex w-full max-w-sm flex-col gap-2"
      >
        {toasts.map((toast) => {
          const Icon = VARIANT_ICONS[toast.variant];
          return (
            <div
              key={toast.id}
              role="status"
              className={clsx(
                'pointer-events-auto flex items-start gap-3 rounded-md border px-4 py-3 text-sm shadow-lg backdrop-blur',
                VARIANT_STYLES[toast.variant]
              )}
            >
              <Icon className="mt-0.5 h-4 w-4 shrink-0" />
              <p className="flex-1 leading-snug">{toast.message}</p>
              <button
                type="button"
                onClick={() => dismiss(toast.id)}
                title="Fechar"
                aria-label="Fechar aviso"
                className="shrink-0 opacity-60 transition-opacity hover:opacity-100"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
};
