import { useCallback, useEffect, useRef, useState } from 'react';
import { loginErrorMessage } from '@/utils/loginError';

const GSI_SRC = 'https://accounts.google.com/gsi/client';

/** Carrega o script do Google Identity Services uma vez por página. */
function loadGsi(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();

  const existente = document.querySelector<HTMLScriptElement>(`script[src="${GSI_SRC}"]`);
  if (existente) {
    return new Promise((resolve, reject) => {
      existente.addEventListener('load', () => resolve());
      existente.addEventListener('error', () => reject(new Error('gsi')));
    });
  }

  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = GSI_SRC;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('gsi'));
    document.head.appendChild(script);
  });
}

interface Options {
  /** Recebe o ID token do Google. Rejeitar aqui vira a mensagem de erro do hook. */
  onCredential: (credential: string) => Promise<void>;
}

/**
 * Renderiza o botão oficial do Google e devolve o ID token.
 *
 * O botão é desenhado pelo próprio Google dentro de um container nosso — é
 * exigência da plataforma, não escolha de estilo: só o iframe deles tem acesso
 * à sessão do usuário no Google.
 *
 * Sem VITE_GOOGLE_CLIENT_ID o hook fica inerte e enabled volta false, para a
 * tela de login não mostrar um botão que não tem como funcionar.
 */
export function useGoogleSignIn({ onCredential }: Options) {
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim();
  const enabled = !!clientId && clientId !== 'dummy';

  // Ref de callback, e nao useRef: o container pode nascer depois da montagem (tela que mostra
  // "Carregando..." antes, ou o botao que volta depois do "Entrando..."). Com useRef o efeito rodava
  // uma vez com o container nulo e o botao nunca aparecia (cadastro, 05/10/2026).
  const [container, setContainer] = useState<HTMLDivElement | null>(null);
  const containerRef = useCallback((el: HTMLDivElement | null) => setContainer(el), []);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // O callback do Google é registrado uma vez, mas precisa enxergar sempre a
  // versão atual de onCredential — daí a ref em vez da função direto.
  const onCredentialRef = useRef(onCredential);
  onCredentialRef.current = onCredential;

  useEffect(() => {
    if (!enabled || !container) return;
    let cancelado = false;

    loadGsi()
      .then(() => {
        if (cancelado || !container) return;

        google.accounts.id.initialize({
          client_id: clientId!,
          callback: (response) => {
            setError('');
            setIsLoading(true);
            onCredentialRef.current(response.credential)
              .catch((err: unknown) => {
                setError(loginErrorMessage(err, {
                  unauthorized: 'A conta Google não foi aceita pelo PrimeVest.',
                  fallback: 'Não foi possível entrar com o Google.',
                }));
              })
              .finally(() => setIsLoading(false));
          },
        });

        google.accounts.id.renderButton(container, {
          type: 'standard',
          theme: 'filled_black',
          size: 'large',
          text: 'continue_with',
          shape: 'rectangular',
          logo_alignment: 'center',
          locale: 'pt-BR',
        });
      })
      .catch(() => {
        if (!cancelado) setError('Não foi possível carregar o login do Google');
      });

    return () => {
      cancelado = true;
    };
  }, [clientId, enabled, container]);

  return { containerRef, enabled, error, isLoading };
}

declare global {
  interface Window {
    google?: { accounts?: { id?: unknown } };
  }
}
