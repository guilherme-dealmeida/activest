/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Client ID do Google. Mesmo valor que a API recebe em GOOGLE_CLIENT_ID —
   * os dois lados precisam bater: o navegador pede o token PARA esta audience
   * e o backend só aceita token emitido para ela. Vazio esconde o botão.
   * Não é segredo: vai no bundle, visível a quem abrir o DevTools.
   */
  readonly VITE_GOOGLE_CLIENT_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

/**
 * Superfície mínima do Google Identity Services, carregado por script externo
 * (accounts.google.com/gsi/client) e portanto sem tipos no npm.
 */
declare namespace google.accounts.id {
  interface CredentialResponse {
    /** ID token JWT assinado pelo Google. É o que vai para POST /auth/google. */
    credential: string;
    select_by?: string;
  }

  interface IdConfiguration {
    client_id: string;
    callback: (response: CredentialResponse) => void;
    auto_select?: boolean;
    cancel_on_tap_outside?: boolean;
    use_fedcm_for_prompt?: boolean;
  }

  interface ButtonConfiguration {
    type?: 'standard' | 'icon';
    theme?: 'outline' | 'filled_blue' | 'filled_black';
    size?: 'large' | 'medium' | 'small';
    text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin';
    shape?: 'rectangular' | 'pill' | 'circle' | 'square';
    logo_alignment?: 'left' | 'center';
    width?: number;
    locale?: string;
  }

  function initialize(config: IdConfiguration): void;
  function renderButton(parent: HTMLElement, options: ButtonConfiguration): void;
  function disableAutoSelect(): void;
}
