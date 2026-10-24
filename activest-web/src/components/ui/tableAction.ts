/**
 * Botão de ação nas listas de tabela (coluna AÇÕES): Listar, Comprar, Vender,
 * Editar, Restaurar, Excluir.
 *
 * Todos no amarelo do tema (accent), em repouso e no hover — o hover só acende
 * um fundo. Antes cada ícone tinha a sua cor de hover (verde, vermelho, branco)
 * e o resto do tempo ficava cinza; a ação passa a ser reconhecida pela cor sem
 * precisar passar o mouse. O que diz qual é a ação é o ícone e o title.
 */
export const TABLE_ACTION_CLASS =
  'p-1 rounded text-accent hover:bg-accent/15 transition-colors disabled:opacity-50 disabled:cursor-not-allowed';
