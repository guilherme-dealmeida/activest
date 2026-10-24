/**
 * A mensagem de erro que o backend mandou, quando mandou.
 *
 * A API devolve a regra violada em texto ("A venda de Tesouro Direto é do
 * ativo…"), e é ela que o usuário precisa ler. Sem mensagem — rede fora,
 * resposta vazia —, quem chama decide o texto padrão.
 */
export const apiMessage = (error: unknown): string | undefined => {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const { response } = error as { response?: { data?: { message?: string; error?: string } } };
    return response?.data?.message || response?.data?.error || undefined;
  }
  return undefined;
};

/**
 * Mensagem para formulário: o primeiro erro de campo do 400 (Bean Validation, já em português) ou a
 * mensagem da regra (422/429/403). Sem nenhuma das duas, o texto padrão de quem chama.
 */
export const formErrorMessage = (error: unknown, fallback: string): string => {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const { response } = error as { response?: { status?: number; data?: { message?: string; errors?: Record<string, string> } } };
    if (!response) return 'Não foi possível conectar ao servidor. Verifique se a API do Activest está no ar.';
    const fields = response.data?.errors;
    if (response.status === 400 && fields) {
      const first = Object.values(fields)[0];
      if (first) return first;
    }
    if (response.data?.message) return response.data.message;
  }
  return fallback;
};
