/**
 * Ícone de cada tipo de ativo (09/11/2026): o losango do Activest na cor do tipo, em public/images/tipos (cópias de
 * 128 px das imagens originais de public/images). A cor do arquivo é a mesma do asset_types.color_hex do tipo
 * (V2 + V22); se a cor de um tipo mudar na base, troque o arquivo aqui também.
 *
 * O original tem fundo escuro da própria cor e fica estranho no tema claro: lá vai a versão `_t` (só o losango,
 * fundo transparente, recortada no losango — 09/11/2026).
 */
const TYPE_ICON_COLOR: Record<string, string> = {
  ACAO: 'vermelho',
  FII: 'verde',
  RENDA_FIXA: 'azul',
  TESOURO_DIRETO: 'amarelo',
  ETF: 'roxo',
  FUNDO_INVESTIMENTO: 'marrom',
  BDR: 'magenta',
  STOCK: 'teal',
  REIT: 'verde-limao',
  ETF_INTERNACIONAL: 'indigo',
  CRIPTO: 'laranja',
  OUTROS: 'ciano',
};

/** Caminho do ícone pelo nome interno do tipo (ACAO, FII...) e pelo tema; null para tipo sem ícone. */
export const assetTypeIcon = (typeName: string | null | undefined, isDark = true): string | null => {
  const color = typeName ? TYPE_ICON_COLOR[typeName] : undefined;
  return color ? `/images/tipos/${color}${isDark ? '' : '_t'}.png` : null;
};
