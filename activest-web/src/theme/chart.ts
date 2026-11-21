/**
 * Tema dos gráficos.
 *
 * Antes cada gráfico declarava o seu: grade, cor do eixo, fundo do balão. São
 * oito cópias da mesma coisa, e uma delas já estava desatualizada (o gráfico
 * da curva usava cinza de eixo diferente do resto). Agora a moldura vem
 * daqui, e o chrome do gráfico acompanha o tema sem nenhum ternário.
 *
 * As cores categóricas (a série da carteira, cada benchmark, cada tipo de
 * ativo) continuam no módulo que as conhece: são identidade de série, não
 * tema — trocar de tema não pode repintar o CDI.
 */
import { useTheme } from '@/context/ThemeContext';

/** Moldura do gráfico: grade, eixo, cursor e balão. Não depende do tema em JS — as variáveis resolvem na pintura. */
export const chartTheme = () => ({
  grid: 'var(--chart-grid)',
  axis: 'var(--chart-axis)',
  cursor: 'var(--chart-cursor)',
  tipBg: 'var(--chart-tip-bg)',
  tipLine: 'var(--chart-tip-line)',
  tipText: 'var(--content)',
  tipMuted: 'var(--content-3)',
  /** Rótulo do eixo e da série. */
  label: 'var(--content-2)',
  /** Contorno do ponto ativo: a cor da superfície onde o ponto está. */
  surface: 'var(--surface)',
});

/** A moldura com o tema em mãos, para quem também precisa da paleta categórica. */
export const useChartTheme = () => {
  const { isDark } = useTheme();
  return { isDark, ...chartTheme() };
};

/**
 * Cores da Rentabilidade (docs/planos/PLANO_RENTABILIDADE.md, 6.1), validadas com o validador da skill de
 * visualização contra as superfícies do tema: banda de luminosidade, croma, separação para daltonismo entre
 * vizinhos e contraste. A carteira usa o dourado da marca escurecido para a banda; os benchmarks seguem a ordem
 * categórica que passou (azul, verde-água, laranja, violeta, magenta, verde). A cor é do benchmark, nunca da
 * posição na lista: tirar um não repinta os outros.
 */
const DARK: Record<string, string> = {
  CARTEIRA: '#ad8a32',
  CDI: '#3987e5',
  IBOV: '#199e70',
  IPCA: '#d95926',
  IFIX: '#9085e9',
  IPCA_MAIS: '#d55181',
  BTC: '#008300',
  SP500_IVVB11: '#e66767',
  SELIC: '#8c8c8c',
};
const LIGHT: Record<string, string> = {
  CARTEIRA: '#9a7a26',
  CDI: '#2a78d6',
  IBOV: '#1baf7a',
  IPCA: '#eb6834',
  IFIX: '#6250d6',
  IPCA_MAIS: '#e87ba4',
  BTC: '#008300',
  SP500_IVVB11: '#e34948',
  SELIC: '#7a7a7a',
};

/** Cor de uma série: CARTEIRA ou o código do benchmark ("IPCA_MAIS:6.0" usa a do IPCA_MAIS). */
export const seriesColor = (key: string, isDark: boolean) => {
  const code = key.split(':')[0];
  return (isDark ? DARK : LIGHT)[code] ?? (isDark ? '#8c8c8c' : '#7a7a7a');
};
