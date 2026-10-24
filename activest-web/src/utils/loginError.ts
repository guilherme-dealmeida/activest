/**
 * Mensagem de erro das telas de entrada (email/senha e Google).
 *
 * Até 23/09/2026 tudo virava "Email ou senha inválidos": API fora do ar,
 * origem recusada pelo CORS e senha errada eram a mesma frase, e o usuário ia
 * conferir a senha quando o problema era outro. Aqui cada situação tem a sua.
 *
 * O status decide, não o texto da API: no 401 ela devolve "Falha de
 * autenticação", genérico demais para a tela.
 */

interface HttpErrorShape {
  response?: {
    status?: number;
    data?: unknown;
  };
}

const UNREACHABLE = 'Não foi possível conectar ao servidor. Verifique se a API do Activest está no ar.';
const FORBIDDEN = 'O servidor recusou o pedido vindo deste endereço. Abra o Activest por http://localhost:5173.';
const INVALID_INPUT = 'Informe um email válido e a senha.';
const SERVER_ERROR = 'O servidor encontrou um erro ao processar a entrada. Tente novamente em instantes.';

/** A mensagem que a API mandou no corpo, quando o corpo é o JSON dela. */
const bodyMessage = (data: unknown): string | undefined => {
  if (typeof data === 'object' && data !== null && 'message' in data) {
    const message = (data as { message?: unknown }).message;
    return typeof message === 'string' && message.trim() ? message : undefined;
  }
  return undefined;
};

interface LoginErrorOptions {
  /** Texto do 401. Email/senha e Google recusam por motivos diferentes. */
  unauthorized: string;
  /** Texto de qualquer outro caso sem mensagem própria. */
  fallback: string;
}

export const loginErrorMessage = (error: unknown, { unauthorized, fallback }: LoginErrorOptions): string => {
  const { response } = (error ?? {}) as HttpErrorShape;

  // Sem resposta nenhuma: rede, servidor desligado sem proxy no meio.
  if (!response || response.status === undefined) {
    return UNREACHABLE;
  }

  const { status, data } = response;
  const message = bodyMessage(data);

  // Com a API parada, quem responde é o proxy do Vite: 500 sem o JSON da API
  // (ou 502/503/504, conforme o proxy). 500 COM mensagem é erro de verdade
  // dentro da API, e cai mais abaixo.
  if ([502, 503, 504].includes(status) || (status === 500 && !message)) {
    return UNREACHABLE;
  }

  switch (status) {
    case 401:
      return unauthorized;
    case 400:
      return INVALID_INPUT;
    case 403:
      // 403 com a mensagem da API é regra (ex.: cadastro desativado); sem ela, é o CORS recusando a origem.
      return message || FORBIDDEN;
    case 500:
      return SERVER_ERROR;
    default:
      // 422 e afins: a regra violada, no texto da API ("Token do Google
      // inválido ou expirado").
      return message || fallback;
  }
};
