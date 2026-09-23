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
