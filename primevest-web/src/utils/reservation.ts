import { ReservationType } from '@/types';

/** Rótulos do tipo de estratégia de reserva, iguais nas duas abas. */
export const RESERVATION_TYPE_LABELS: Record<ReservationType, string> = {
  PERCENTAGE: 'Por Porcentagem',
  VALUE: 'Por Valor',
};

/**
 * Cores do gráfico "Distribuição Estratégica Reserva", definidas no escopo.
 * Não vêm de asset_types.color_hex: reserva e geração de renda não são tipos
 * de ativo, são a divisão da carteira inteira em dois blocos.
 */
export const RESERVATION_COLOR = '#8a00c4';
export const INCOME_COLOR = '#7d7e7f';
