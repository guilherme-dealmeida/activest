import { formatPercentage } from '@/utils/format';
import type { AllocationData } from './AllocationPieChart';
import type { ChartLegendItem } from './ChartLegend';

/** Itens da legenda vertical: participação e, quando a fatia tem, o ideal da estratégia. */
export const legendItemsOf = (data: AllocationData[]): ChartLegendItem[] =>
  data.map(item => ({
    name: item.name,
    color: item.color,
    value: formatPercentage(item.percentage),
    ideal: item.ideal !== undefined ? formatPercentage(item.ideal) : undefined,
  }));
