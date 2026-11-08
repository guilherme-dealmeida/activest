import { useMemo } from 'react';
import { useElementWidth } from './useElementWidth';
import { Granularity, calendarTicks, maxTicksFor } from '@/utils/calendarTicks';

/**
 * Eixo X de calendário para um gráfico por categoria (docs/planos/PLANO_CALENDARIO_GRAFICOS.md): o {@code ref} vai no
 * contêiner do gráfico (para medir a largura); {@code axisProps} vai no XAxis — marcos de calendário, todos exibidos
 * (`interval={0}`), com o rótulo da granularidade escolhida.
 */
export function useCalendarAxis(dates: string[], minGranularity: Granularity = 'DAY') {
  const { ref, width } = useElementWidth();
  const axis = useMemo(() => calendarTicks(dates, maxTicksFor(width), minGranularity), [dates, width, minGranularity]);
  return {
    ref,
    axis,
    axisProps: { ticks: axis.ticks, interval: 0 as const, tickFormatter: (v: string) => axis.format(v) },
  };
}
