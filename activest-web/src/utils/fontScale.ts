/**
 * Tamanho da tela (docs/planos/PLANO_CUSTOMIZAR_TAMANHOS.md). A tela inteira é medida em rem; o tamanho
 * "Pequena" reduz a fonte raiz (`html.font-small` em index.css) e tudo acompanha, como um zoom.
 */

export type FontSize = 'large' | 'small';

/** Proporção de cada tamanho sobre a fonte raiz padrão (16 px). D1: Pequena = 87,5% (14 px). */
export const FONT_SCALE: Record<FontSize, number> = { large: 1, small: 0.875 };

/** Classe do `<html>` no tamanho Pequena. */
export const SMALL_FONT_CLASS = 'font-small';

const BASE_PX = 16;

/**
 * Px de desenho → rem, para o que precisa de um tamanho fixo e tem de acompanhar o tamanho da tela
 * (fontes dos gráficos do Recharts, que aceitam a medida CSS). `rem(11)` → "0.6875rem".
 */
export const rem = (px: number): string => `${px / BASE_PX}rem`;

/** O que vem do servidor (`LARGE`/`SMALL`) para o valor da tela; qualquer outra coisa é o padrão. */
export const fontSizeFromUser = (value: string | null | undefined): FontSize =>
  value === 'SMALL' ? 'small' : 'large';

/** O valor da tela para o que o servidor grava. */
export const fontSizeToUser = (size: FontSize): 'LARGE' | 'SMALL' => (size === 'small' ? 'SMALL' : 'LARGE');
