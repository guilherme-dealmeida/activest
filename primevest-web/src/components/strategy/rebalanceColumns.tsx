/**
 * Grade de colunas compartilhada pelas duas tabelas do Rebalanceamento.
 *
 * As tabelas ficam uma embaixo da outra e têm as mesmas oito colunas. Com o
 * layout automático do navegador, a largura de cada coluna saía do conteúdo —
 * "Tipo de Estratégia (i) / Por Valor" em cima, "Tipo Ativo / Ações" embaixo —
 * e as colunas das duas tabelas ficavam desalinhadas. Com `table-fixed` e as
 * mesmas larguras aqui, a coluna de descrição não empurra as outras.
 */
export const REBALANCE_TABLE_CLASS = 'w-full min-w-[1100px] table-fixed text-sm text-left';

// Descrição + sete colunas numéricas/sugestão: 16% + 7 × 12% = 100%.
const WIDTHS = ['16%', '12%', '12%', '12%', '12%', '12%', '12%', '12%'];

export const RebalanceColGroup = () => (
  <colgroup>
    {WIDTHS.map((w, i) => (
      <col key={i} style={{ width: w }} />
    ))}
  </colgroup>
);
