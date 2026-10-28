import { useQuery } from '@tanstack/react-query';
import { assetsApi } from '@/api/assets';
import { AssetType } from '@/types';
import { AssetTypeSeries } from '@/utils/dividendPeriods';

/**
 * Cores da Evolução dos Proventos (docs/planos/PLANO_DIVIDENDOS_POR_PERIODO.md, D5 e R2).
 *
 * O recebido se divide por tipo de ativo, na **cor do tipo de ativo do cadastro** (`asset_types.color_hex`, a mesma
 * da Alocação, do Dashboard e da Estratégia — pedido do Guilherme, 27/10/2026): FIIs verde, Ações vermelho... A cor é
 * do tipo, nunca da posição na pilha, e o vão de 2 px entre os segmentos separa as partes vizinhas.
 *
 * O a receber é um estado, não um tipo: um bloco só, cinza claro hachurado a 45° (a textura é o segundo canal, para
 * não depender só da cor).
 */

/** Tipo sem cor no cadastro (ou cadastro ainda carregando): o dourado da marca. */
const FALLBACK = '#c9a84c';

export type ColoredSeries = AssetTypeSeries & { color: string };

/** Séries com a cor do cadastro de tipos de ativo (`GET /assets/types`, o mesmo cache do seletor de tipo). */
export const useColoredSeries = (types: AssetTypeSeries[]): ColoredSeries[] => {
  const { data: assetTypes = [] } = useQuery<AssetType[]>({ queryKey: ['assetTypes'], queryFn: assetsApi.getTypes });
  const byId = new Map(assetTypes.map(t => [t.id, t.colorHex]));
  return types.map(t => ({ ...t, color: (t.id != null ? byId.get(t.id) : undefined) || FALLBACK }));
};

/** Hachura do a receber: fundo claro e listras do mesmo cinza. */
export const toReceiveColors = (isDark: boolean) =>
  isDark ? { stripe: '#8c8c8c', wash: 'rgba(140, 140, 140, 0.28)' } : { stripe: '#9a9a9a', wash: 'rgba(122, 122, 122, 0.18)' };

/** A mesma hachura em CSS, para as amostras da legenda. */
export const toReceiveSwatch = (isDark: boolean): string => {
  const { stripe, wash } = toReceiveColors(isDark);
  return `repeating-linear-gradient(45deg, ${stripe} 0 2px, ${wash} 2px 5px)`;
};

/** Dourado da marca para a intensidade das células da tabela (sequencial de uma cor só). */
export const heatBackground = (k: number): string => `rgba(201, 168, 76, ${(0.06 + 0.5 * Math.max(0, Math.min(1, k))).toFixed(3)})`;
